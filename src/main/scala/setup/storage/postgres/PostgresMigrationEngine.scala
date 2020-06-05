package setup.storage.postgres

import setup.storage._
import cats._, implicits._
import domain.instances._
import scala.util.Try
import setup.MigrationStep
import domain.SyntaxTree
import setup._
import postgres.utils._

import setup.storage.postgres.SQLMigrationStep._

import domain._
import org.jooq.util.postgres.PostgresDataType

class PostgresMigrationEngine[M[_]: Monad](syntaxTree: SyntaxTree)
    extends MigrationEngine[Postgres, M] {
  override def migrate(
      migrationSteps: Vector[MigrationStep]
  ): M[Vector[Try[Unit]]] = Monad[M].pure(Vector(Try(())))

  def migration(prevTree: SyntaxTree = SyntaxTree.empty): PostgresMigration =
    migration(inferedMigrationSteps(syntaxTree, prevTree))

  private[postgres] def inferedMigrationSteps(
      currentTree: SyntaxTree,
      prevTree: SyntaxTree
  ): Vector[MigrationStep] = {
    if (prevTree.isEmpty) {
      currentTree.models.map(CreateModel(_)).toVector
    } else {
      val currentModelMigs = currentTree.models.map(ModelMig(_)).toVector
      val prevModelMigs = prevTree.models.map(ModelMig(_)).toVector

      val newModels = currentModelMigs
        .diff(prevModelMigs)
        .map(modelMig => CreateModel(currentTree.modelsById(modelMig.modelId)))
      val deletedModels = prevModelMigs
        .diff(currentModelMigs)
        .map(modelMig => DeleteModel(prevTree.modelsById(modelMig.modelId)))
      val renamedModels = for {
        currentModel <- currentModelMigs
        prevModel <- prevModelMigs.find(_.migId == currentModel.migId)
        if currentModel.modelId != prevModel.modelId
      } yield RenameModel(prevModel.modelId, currentModel.modelId)

      val fieldMigrationSteps = {
        for {
          currentModelMig <- currentModelMigs
          prevModelMig <- prevModelMigs.find(_.migId == currentModelMig.migId)
          currentModel = currentTree.modelsById(currentModelMig.modelId)
          prevModel = prevTree.modelsById(prevModelMig.modelId)
        } yield {

          val newFields = currentModelMig.fields
            .diff(prevModelMig.fields)
            .map { fieldMig =>
              val field = currentModel.fieldsById(fieldMig.fieldId)
              AddField(field, prevModel)
            }

          val deletedFields = prevModelMig.fields
            .diff(currentModelMig.fields)
            .map { fieldMig =>
              val field = prevModel.fieldsById(fieldMig.fieldId)
              DeleteField(field, prevModel)
            }

          val renamedFields = for {
            currentFieldMig <- currentModelMig.fields
            prevFieldMig <- prevModelMig.fields.find(
              _.migId == currentFieldMig.migId
            )
            if currentFieldMig.fieldId != prevFieldMig.fieldId
          } yield
            RenameField(
              prevFieldMig.fieldId,
              currentFieldMig.fieldId,
              prevModel
            )

          val changeTypeFields = {
            for {
              currentFieldMig <- currentModelMig.fields
              prevFieldMig <- prevModelMig.fields.find(
                _.migId == currentFieldMig.migId
              )
              currentField = currentModel.fieldsById(currentFieldMig.fieldId)
              prevField = prevModel.fieldsById(prevFieldMig.fieldId)
              if currentField.ptype != prevField.ptype
            } yield
              ChangeManyFieldTypes(
                prevModel,
                Vector(
                  ChangeFieldType(currentField, currentField.ptype, ???, ???)
                )
              )
          }.foldLeft[Option[ChangeManyFieldTypes]](None) {
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

          val fieldMigrationSteps: Vector[MigrationStep] =
            changeTypeFields match {
              case Some(changeTypeFields) =>
                Vector(
                  newFields ++ deletedFields ++ renamedFields,
                  Vector(changeTypeFields)
                ).flatten
              case None => newFields ++ deletedFields ++ renamedFields
            }

          fieldMigrationSteps
        }
      }.flatten

      newModels ++ deletedModels ++ renamedModels ++ fieldMigrationSteps
    }
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
        migration(AddField(field, model)).steps
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
              s"${model.id}Id",
              model.primaryField.ptype match {
                case PString => PostgresDataType.TEXT.nullable(false)
                case PInt    => PostgresDataType.INT.nullable(false)
                case t =>
                  throw new InternalError(
                    s"Primary field in model `${model.id}` has type `${domain.utils.displayPType(t)}` and primary fields can only be of type `Int` or type `String`. This error is unexpected and must be reviewed by the creators of Pragma."
                  )
              },
              isNotNull = true,
              isAutoIncrement = false,
              isPrimaryKey = false,
              isUUID = false,
              isUnique = false,
              foreignKey = Some(ForeignKey(model.id, model.primaryField.id))
            )

            val valueOrReferenceColumn = innerModel match {
              case Some(otherModel) =>
                ColumnDefinition(
                  s"${otherModel.id}Id",
                  otherModel.primaryField.ptype match {
                    case PString => PostgresDataType.TEXT.nullable(false)
                    case PInt    => PostgresDataType.INT.nullable(false)
                    case t =>
                      throw new InternalError(
                        s"Primary field in model `${otherModel.id}` has type `${domain.utils.displayPType(t)}` and primary fields can only be of type `Int` or type `String`. This error is unexpected and must be reviewed by the creators of Pragma."
                      )
                  },
                  isNotNull = true,
                  isAutoIncrement = false,
                  isPrimaryKey = false,
                  isUUID = false,
                  isUnique = false,
                  foreignKey =
                    Some(ForeignKey(otherModel.id, otherModel.primaryField.id))
                )
              case None =>
                ColumnDefinition(
                  name = s"${field.id}",
                  dataType = toPostgresType(
                    field.ptype match {
                      case POption(PArray(t)) => t
                      case PArray(t)          => t
                      case t =>
                        throw new InternalError(
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
      val alterTableStatement = AlterTable(
        model.id,
        AlterTableAction.AddColumn(
          ColumnDefinition(
            name = field.id,
            dataType = fieldPostgresType(field)(syntaxTree).get,
            isNotNull = !field.isOptional,
            isUnique = field.directives.exists(_.id == "unique"),
            isPrimaryKey = field.directives.exists(_.id == "primary"),
            isAutoIncrement = field.directives.exists(_.id == "autoIncrement"),
            isUUID = field.directives.exists(_.id == "uuid"),
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

// `ModelMig#equals` and `FieldMig#equals` assumes that the `Validator` has validated
// that no model or field has the same name in the same scope nor the same migration id

case class ModelMig(modelId: String, migId: Int, fields: Vector[FieldMig]) {
  override def equals(that: Any): Boolean = that match {
    case that: ModelMig => migId == that.migId
    case _              => false
  }
}
object ModelMig {
  def apply(model: PModel): ModelMig =
    ModelMig(model.id, ???, model.fields.map(FieldMig(_)).toVector)
}
case class FieldMig(
    fieldId: String,
    migId: Int,
    directives: Vector[Directive]
) {
  override def equals(that: Any): Boolean = that match {
    case that: FieldMig => migId == that.migId
    case _              => false
  }
}
object FieldMig {
  def apply(field: PModelField): FieldMig =
    FieldMig(field.id, ???, field.directives.toVector)
}
