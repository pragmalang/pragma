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
import OnDeleteAction.Cascade

class PostgresMigrationEngine[M[_]: Monad](syntaxTree: SyntaxTree)
    extends MigrationEngine[Postgres[M], M] {
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
      val prevIndexedModel = prevTree.models.map(IndexedModel(_)).toVector

      val newModels = currentIndexedModels
        .diff(prevIndexedModel)
        .map(
          indexedModel => CreateModel(currentTree.modelsById(indexedModel.id))
        )

      val deletedModels = prevIndexedModel
        .diff(currentIndexedModels)
        .map(indexedModel => DeleteModel(prevTree.modelsById(indexedModel.id)))

      val renamedModels = for {
        currentModel <- currentIndexedModels
        prevModel <- prevIndexedModel.find(_.index == currentModel.index)
        if currentModel.id != prevModel.id
      } yield RenameModel(prevModel.id, currentModel.id)

      val fieldMigrationSteps = for {
        currentIndexedModel <- currentIndexedModels
        prevIndexedModel <- prevIndexedModel.find(
          _.index == currentIndexedModel.index
        )
        currentModel = currentTree.modelsById(currentIndexedModel.id)
        prevModel = prevTree.modelsById(prevIndexedModel.id)
        newFields = currentIndexedModel.indexedFields
          .diff(prevIndexedModel.indexedFields)
          .map { indexedField =>
            val field = currentModel.fieldsById(indexedField.id)
            AddField(field, prevModel)
          }

        deletedFields = prevIndexedModel.indexedFields
          .diff(currentIndexedModel.indexedFields)
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
        } yield
          ChangeManyFieldTypes(
            prevModel,
            Vector(
              ChangeFieldType(
                currentField,
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
    steps.map(migration).foldLeft(PostgresMigration.empty)(_ ++ _)

  private[postgres] def migration(
      step: MigrationStep
  ): PostgresMigration = step match {
    case CreateModel(model) => {
      val createTableStatement = CreateTable(model.id, Vector.empty)
      val addColumnStatements = model.fields.flatMap { field =>
        migration(AddField(field, model)).steps(syntaxTree)
      }
      PostgresMigration(
        Vector(Vector(createTableStatement), addColumnStatements).flatten,
        Vector.empty
      )
    }
    case RenameModel(modelId, newId) =>
      PostgresMigration(Vector(RenameTable(modelId, newId)), Vector.empty)
    case DeleteModel(model) =>
      PostgresMigration(Vector(DropTable(model.id)), Vector.empty)
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
                    },
                    false
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
          case t => None
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
                      case Left(value) => None
                      case Right(fk)   => Some(fk)
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
                  case Right(value) => Vector.empty
                }
              case None => Vector.empty
            },
            Vector.empty
          )
        case Some(alterTableStatement) =>
          PostgresMigration(
            g match {
              case Some(value) =>
                value match {
                  case Left(createArrayTableStatement) =>
                    Vector(alterTableStatement, createArrayTableStatement)
                  case Right(value) => Vector(alterTableStatement)
                }
              case None => Vector(alterTableStatement)
            },
            Vector.empty
          )
      }
    }
    case RenameField(fieldId, newId, model) =>
      PostgresMigration(
        Vector(
          AlterTable(model.id, AlterTableAction.RenameColumn(fieldId, newId))
        ),
        Vector.empty
      )
    case DeleteField(field, model) =>
      PostgresMigration(
        Vector(
          AlterTable(model.id, AlterTableAction.DropColumn(field.id, true))
        ),
        Vector.empty
      )
    case UndeleteField(field, model) =>
      migration(AddField(field, model))
    case ChangeManyFieldTypes(model, changes) => {
      // TODO: Implement this after finishing `PostgresQueryEngine` because it's eaiser to use it than plain SQL
      val tempTableName = "__migration__" + scala.util.Random.nextInt(99999)
      val columns: Vector[ColumnDefinition] = ???
      val createTempTable = s"""
      CREATE TABLE $tempTableName (${columns.mkString(", \n")})
      """
      println(createTempTable)
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
