package running.storage.postgres

import running.storage._
import cats._
import scala.util.Try
import domain.SyntaxTree
import postgres.utils._
import domain.utils._

import SQLMigrationStep._

import domain._
import domain.utils.UserError
import domain.DomainImplicits._
import OnDeleteAction.Cascade

import doobie._

import running.storage.postgres.instances._
import spray.json.JsObject

class PostgresMigrationEngine[M[_]: Monad](syntaxTree: SyntaxTree)
    extends MigrationEngine[Postgres[M], M] {
  implicit val st = syntaxTree
  override def migrate(
      migrationSteps: Vector[MigrationStep]
  ): M[Vector[Try[Unit]]] = Monad[M].pure(Vector(Try(())))

  def initialMigration = migration(SyntaxTree.empty, (_, _) => false)

  def migration(
      prevTree: SyntaxTree = SyntaxTree.empty,
      thereExistData: (ModelId, FieldId) => Boolean
  ): PostgresMigration =
    migration(inferedMigrationSteps(syntaxTree, prevTree, thereExistData))

  private[postgres] def inferedMigrationSteps(
      currentTree: SyntaxTree,
      prevTree: SyntaxTree,
      thereExistData: (ModelId, FieldId) => Boolean
  ): Vector[MigrationStep] =
    if (prevTree.models.isEmpty)
      currentTree.models.map(CreateModel(_)).toVector
    else {
      val currentIndexedModels =
        currentTree.models.map(IndexedModel(_)).toVector
      val prevIndexedModels = prevTree.models.map(IndexedModel(_)).toVector

      val renamedModels = for {
        currentModel <- currentIndexedModels
        prevModel <- prevIndexedModels.find(_.index == currentModel.index)
        if currentModel.id != prevModel.id
      } yield RenameModel(prevModel.id, currentModel.id)

      val newModels = currentIndexedModels
        .filter(
          indexedModel =>
            !prevIndexedModels.exists(_.index == indexedModel.index)
        )
        .map(
          indexedModel => CreateModel(currentTree.modelsById(indexedModel.id))
        )
        .filter(
          createModel =>
            !renamedModels
              .exists(_.newId == createModel.model.id)
        )

      val deletedModels = prevIndexedModels
        .filter(
          indexedModel =>
            !currentIndexedModels.exists(_.index == indexedModel.index)
        )
        .map(indexedModel => DeleteModel(prevTree.modelsById(indexedModel.id)))
        .filter(
          deleteModel =>
            !renamedModels
              .exists(
                _.prevModelId == deleteModel.prevModel.id
              )
        )

      // Models that are not deleted, not renamed, not new, and their fields may have changed
      val unrenamedUndeletedPreviousIndexedModels =
        currentIndexedModels.filter { currentIndexedModel =>
          !renamedModels
            .exists(
              _.prevModelId == prevIndexedModels
                .find(_.index == currentIndexedModel.index)
                .get
                .id
            ) && !deletedModels
            .exists(
              _.prevModel.id == currentIndexedModel.id
            ) && !newModels
            .exists(
              _.model.id == currentIndexedModel.id
            )
        }

      val fieldMigrationSteps = for {
        currentIndexedModel <- unrenamedUndeletedPreviousIndexedModels
        prevIndexedModel <- prevIndexedModels.find(
          _.index == currentIndexedModel.index
        )
        currentModel = currentTree.modelsById(currentIndexedModel.id)
        prevModel = prevTree.modelsById(prevIndexedModel.id)
        newFields = currentIndexedModel.indexedFields
          .filter(
            indexedField =>
              !prevIndexedModel.indexedFields
                .exists(_.index == indexedField.index)
          )
          .map { indexedField =>
            val field = currentModel.fieldsById(indexedField.id)
            AddField(field, prevModel)
          }

        deletedFields = prevIndexedModel.indexedFields
          .filter(
            indexedField =>
              !currentIndexedModel.indexedFields
                .exists(_.index == indexedField.index)
          )
          .map { indexedField =>
            val field = prevModel.fieldsById(indexedField.id)
            DeleteField(field, prevModel)
          }

        renamedFields = for {
          currentIndexedField <- currentIndexedModel.indexedFields
          prevIndexedField <- prevIndexedModel.indexedFields.find(
            _.index == currentIndexedField.index
          )
          if currentIndexedField.id != prevIndexedField.id
        } yield
          RenameField(
            prevIndexedField.id,
            currentIndexedField.id,
            prevModel
          )

        changeTypeFields = (for {
          currentIndexedField <- currentIndexedModel.indexedFields
          prevIndexedField <- prevIndexedModel.indexedFields.find(
            _.index == currentIndexedField.index
          )
          currentField = currentModel.fieldsById(currentIndexedField.id)
          prevField = prevModel.fieldsById(prevIndexedField.id)
          if currentField.ptype != prevField.ptype
          if (prevField.ptype match {
            case _ if prevField.ptype.innerPReference.isDefined =>
              prevField.ptype.innerPReference
                .filter(
                  ref => !renamedModels.exists(_.prevModelId == ref.id)
                )
                .isDefined
            case _ => true
          })
        } yield
          ChangeManyFieldTypes(
            prevModel,
            Vector(
              ChangeFieldType(
                prevField,
                currentField.ptype,
                currentField.directives.find(_.id == "typeTransformer") match {
                  case Some(typeTransformerDir) =>
                    typeTransformerDir.args.value("typeTransformer") match {
                      case func: ExternalFunction => Some(func)
                      case func: BuiltinFunction  => Some(func)
                      case pvalue => {
                        val found = displayPType(pvalue.ptype)
                        val required =
                          s"${displayPType(prevField.ptype)} => ${displayPType(currentField.ptype)}"
                        throw new InternalException(
                          s"""
                          |Type mismatch on directive `typeTransformer` on field `${currentModel}.${currentField}`
                          |found: `${found}`
                          |required: `${required}`
                          """.tail.stripMargin
                        )
                      }
                    }
                  case None if thereExistData(prevModel.id, prevField.id) => {
                    val requiredFunctionType =
                      s"${displayPType(prevField.ptype)} => ${displayPType(currentField.ptype)}"
                    throw UserError(
                      s"Field `${currentModel}.${currentField}` type has changed, and Pragma needs a transformation function `${requiredFunctionType}` to transform existing data to the new type"
                    )
                  }
                  case None => None
                },
                currentField.directives
                  .find(_.id == "reverseTypeTransformer") match {
                  case Some(typeTransformerDir) =>
                    typeTransformerDir.args
                      .value("reverseTypeTransformer") match {
                      case func: ExternalFunction => Some(func)
                      case func: BuiltinFunction  => Some(func)
                      case pvalue => {
                        val found = displayPType(pvalue.ptype)
                        val required =
                          s"${displayPType(currentField.ptype)} => ${displayPType(prevField.ptype)}"
                        throw new InternalException(
                          s"""
                          |Type mismatch on directive `reverseTypeTransformer` on field `${currentModel}.${currentField}`
                          |found: `${found}`
                          |required: `${required}`
                          """.tail.stripMargin
                        )
                      }
                    }
                  case None => None
                }
              )
            )
          )).foldLeft[Option[ChangeManyFieldTypes]](None) {
          case (Some(value), e) if value.prevModel == e.prevModel =>
            Some(
              ChangeManyFieldTypes(
                value.prevModel,
                value.changes ++ e.changes
              )
            )
          case (None, e) => Some(e)
          case _         => None
        }

        fieldMigrationSteps: Vector[MigrationStep] = changeTypeFields match {
          case Some(changeTypeFields) =>
            Vector(
              newFields ++ deletedFields ++ renamedFields,
              Vector(changeTypeFields)
            ).flatten
          case None => newFields ++ deletedFields ++ renamedFields
        }
      } yield fieldMigrationSteps

      newModels ++ renamedModels ++ deletedModels ++ fieldMigrationSteps.flatten
    }

  /**
    * CAUTION: This function does not sort `steps` based on
    * the dependency graph of the syntax tree models which
    * means that if `steps` are not sorted correctly, this
    * function might return a `PostgresMigration` that when
    * it's `renderSQL` method is called, the method will return
    * an out-of-order (invalid) SQL statements.
    *
    * This is why it's private. And it's private to the `postgres`
    * package so it can be tested.
    */
  private[postgres] def migration(
      steps: Iterable[MigrationStep]
  ): PostgresMigration =
    steps
      .map(migration(_))
      .foldLeft(PostgresMigration(Vector.empty)(syntaxTree))(
        (acc, migration) =>
          PostgresMigration(acc.unorderedSteps ++ migration.unorderedSteps)(
            syntaxTree
          )
      )

  private[postgres] def migration(
      step: MigrationStep
  ): PostgresMigration = step match {
    case CreateModel(model) => {
      val createTableStatement = CreateTable(model.id, Vector.empty)
      val addColumnStatements = model.fields.flatMap { field =>
        migration(AddField(field, model)).steps
      }
      PostgresMigration(
        Vector(Vector(createTableStatement), addColumnStatements).flatten
      )
    }
    case RenameModel(modelId, newId) =>
      PostgresMigration(Vector(RenameTable(modelId, newId)))
    case DeleteModel(model) =>
      PostgresMigration(Vector(DropTable(model.id)))
    case UndeleteModel(model) => migration(CreateModel(model))
    case AddField(field, model) => {
      val g: Option[Either[CreateTable, ForeignKey]] =
        field.ptype match {
          case POption(PArray(_)) | PArray(_) => {
            val innerModel = field.ptype match {
              case PArray(model: PModel)          => Some(model)
              case PArray(PReference(id))         => syntaxTree.modelsById.get(id)
              case POption(PArray(model: PModel)) => Some(model)
              case POption(PArray(PReference(id))) =>
                syntaxTree.modelsById.get(id)
              case _ => None
            }

            val thisModelReferenceColumn = ColumnDefinition(
              s"source_${model.id}",
              model.primaryField.ptype match {
                case PString => PostgresType.TEXT
                case PInt    => PostgresType.INT8
                case t =>
                  throw new InternalException(
                    s"Primary field in model `${model.id}` has type `${domain.utils.displayPType(t)}` and primary fields can only be of type `Int` or type `String`. This error is unexpected and must be reviewed by the creators of Pragma."
                  )
              },
              isNotNull = true,
              isAutoIncrement = false,
              isPrimaryKey = false,
              isUUID = false,
              isUnique = false,
              foreignKey = Some(
                ForeignKey(
                  model.id,
                  model.primaryField.id,
                  onDelete = Cascade
                )
              )
            )

            val valueOrReferenceColumn = innerModel match {
              case Some(otherModel) =>
                ColumnDefinition(
                  s"target_${otherModel.id}",
                  otherModel.primaryField.ptype match {
                    case PString => PostgresType.TEXT
                    case PInt    => PostgresType.INT8
                    case t =>
                      throw new InternalException(
                        s"Primary field in model `${otherModel.id}` has type `${domain.utils.displayPType(t)}` and primary fields can only be of type `Int` or type `String`. This error is unexpected and must be reviewed by the creators of Pragma."
                      )
                  },
                  isNotNull = true,
                  isAutoIncrement = false,
                  isPrimaryKey = false,
                  isUUID = false,
                  isUnique = false,
                  foreignKey = Some(
                    ForeignKey(
                      otherModel.id,
                      otherModel.primaryField.id,
                      onDelete = Cascade
                    )
                  )
                )
              case None =>
                ColumnDefinition(
                  name = s"${field.id}",
                  dataType = toPostgresType(
                    field.ptype match {
                      case POption(PArray(t)) => t
                      case PArray(t)          => t
                      case t =>
                        throw new InternalException(
                          s"Expected [T] or [T]?, found ${domain.utils.displayPType(t)}"
                        )
                    }
                  )(syntaxTree).get,
                  isNotNull = true,
                  isAutoIncrement = false,
                  isPrimaryKey = false,
                  isUUID = false,
                  isUnique = false,
                  foreignKey = None
                )
            }

            val columns =
              Vector(thisModelReferenceColumn, valueOrReferenceColumn)

            Some(Left(CreateTable(s"${model.id}_${field.id}", columns)))
          }
          case model: PModel =>
            Some(Right(ForeignKey(model.id, model.primaryField.id)))
          case POption(model: PModel) =>
            Some(Right(ForeignKey(model.id, model.primaryField.id)))
          case POption(PReference(id))
              if syntaxTree.modelsById.get(id).isDefined =>
            Some(
              Right(ForeignKey(id, syntaxTree.modelsById(id).primaryField.id))
            )
          case PReference(id) if syntaxTree.modelsById.get(id).isDefined =>
            Some(
              Right(ForeignKey(id, syntaxTree.modelsById(id).primaryField.id))
            )
          case _ => None
        }
      val alterTableStatement = fieldPostgresType(field)(syntaxTree).map {
        postgresType =>
          AlterTable(
            model.id,
            AlterTableAction.AddColumn(
              ColumnDefinition(
                name = field.id,
                dataType = postgresType,
                isNotNull = !field.isOptional,
                isUnique = field.isUnique,
                isPrimaryKey = field.isPrimary,
                isAutoIncrement = field.isAutoIncrement,
                isUUID = field.isUUID,
                foreignKey = g match {
                  case Some(value) =>
                    value match {
                      case Left(_)   => None
                      case Right(fk) => Some(fk)
                    }
                  case None => None
                }
              )
            )
          )
      }
      alterTableStatement match {
        case None =>
          PostgresMigration(
            g match {
              case Some(value) =>
                value match {
                  case Left(createArrayTableStatement) =>
                    Vector(createArrayTableStatement)
                  case Right(_) => Vector.empty
                }
              case None => Vector.empty
            }
          )
        case Some(alterTableStatement) =>
          PostgresMigration(
            g match {
              case Some(value) =>
                value match {
                  case Left(createArrayTableStatement) =>
                    Vector(alterTableStatement, createArrayTableStatement)
                  case Right(_) => Vector(alterTableStatement)
                }
              case None => Vector(alterTableStatement)
            }
          )
      }
    }
    case RenameField(fieldId, newId, model) =>
      PostgresMigration(
        Vector(
          AlterTable(model.id, AlterTableAction.RenameColumn(fieldId, newId))
        )
      )
    case DeleteField(field, model) =>
      PostgresMigration(
        Vector(
          AlterTable(model.id, AlterTableAction.DropColumn(field.id, true))
        )
      )
    case UndeleteField(field, model) =>
      migration(AddField(field, model))
    case ChangeManyFieldTypes(prevModel, changes) => {
      // TODO: Implement this after finishing `PostgresQueryEngine` because it's eaiser to use it than plain SQL
      val newTableTempName = "__temp_table__" + prevModel.id
      val newModelTempDef = prevModel.copy(
        id = newTableTempName,
        fields =
          changes.map(change => change.field.copy(ptype = change.newType)).toSeq
      )
      val createNewTable = migration(CreateModel(newModelTempDef))

      val dropPrevTable = migration(DeleteModel(prevModel))

      val renameNewTable = migration(
        RenameModel(newTableTempName, prevModel.id)
      )

      val stream =
        HC.stream[JsObject](
          s"SELECT * FROM ${prevModel.id.withQuotes};",
          HPS.set(()),
          200
        )

      val streamIO = stream.compile.toVector

      /** TODO:
        *   1) Create the new table with a temp name:
        *   2) Load rows of prev table as a stream in memory:
        *     a) pass the data in each type-changed column to the correct
        *        type transformer if any
        *     b) Type check the value/s returned from the transformer
        *     c) try re-inserting this row in the new table
        *       i) Beware that Postgres auto-generated values don't get regenerated after
        *          this insert. This is merely just copying, make sure that no data is
        *          getting changed without getting passed to the correct transformer.
        */
      ???
    }
  }
}

// `IndexedModel#equals` and `IndexedField#equals` assumes that the `Validator` has validated
// that no model or field has the same name in the same scope nor the same index

case class IndexedModel(
    id: String,
    index: Int,
    indexedFields: Vector[IndexedField]
) {
  override def equals(that: Any): Boolean = that match {
    case that: IndexedModel => index == that.index
    case _                  => false
  }
}
object IndexedModel {
  def apply(model: PModel): IndexedModel =
    IndexedModel(
      model.id,
      model.index,
      model.fields.map(IndexedField(_)).toVector
    )
}
case class IndexedField(
    id: String,
    index: Int,
    directives: Vector[Directive]
) {
  override def equals(that: Any): Boolean = that match {
    case that: IndexedField => index == that.index
    case _                  => false
  }
}
object IndexedField {
  def apply(field: PModelField): IndexedField =
    IndexedField(field.id, field.index, field.directives.toVector)
}
