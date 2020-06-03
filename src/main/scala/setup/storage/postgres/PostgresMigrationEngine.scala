package setup.storage.postgres

import setup.storage._
import cats._
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

  def migration(
      migrationStep: MigrationStep
  ): PostgresMigration = migrationStep match {
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
            isNotNull = fieldPostgresType(field)(syntaxTree).get.nullable(),
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
