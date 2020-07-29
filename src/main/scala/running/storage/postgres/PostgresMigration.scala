package running.storage.postgres

import doobie.util.fragment.Fragment
import doobie.implicits._

import cats.implicits._

import domain.DomainImplicits._
import doobie._

import running.storage.postgres.instances._
import spray.json.JsObject

import utils._
import running.storage._
import SQLMigrationStep._
import OnDeleteAction.Cascade

import domain.SyntaxTree
import domain.utils._

import domain._

case class PostgresMigration(
    unorderedSteps: Vector[SQLMigrationStep]
)(implicit st: SyntaxTree) {
  def steps: Vector[SQLMigrationStep] = {
    val sqlMigrationStepOrdering = new Ordering[SQLMigrationStep] {
      override def compare(x: SQLMigrationStep, y: SQLMigrationStep): Int =
        (x, y) match {
          case (
              AlterTable(_, action: AlterTableAction.AddColumn),
              _: CreateTable
              ) if action.definition.isPrimaryKey =>
            -1
          case (statement: CreateTable, _)
              if st.modelsById.get(statement.name).isDefined =>
            1
          case (statement: CreateTable, _)
              if !st.modelsById.get(statement.name).isDefined =>
            -1
          case (AlterTable(_, action: AlterTableAction.AddColumn), _)
              if action.definition.isPrimaryKey =>
            1
          case (_, _) => 0
        }
    }
    unorderedSteps.sortWith((x, y) => sqlMigrationStepOrdering.gt(x, y))
  }

  def run: ConnectionIO[Unit] = {
    renderSQL match {
      case Some(sql) =>
        Fragment(sql, Nil).update.run.map(_ => ())
      case None => {
        val effects: Vector[Option[ConnectionIO[Unit]]] = steps
          .map {
            case AlterManyFieldTypes(prevModel, changes) => {
              val changesMap: Map[FieldId, ChangeFieldType] =
                changes.foldLeft(Map.empty[FieldId, ChangeFieldType]) {
                  case (acc, change) => acc + (change.field.id -> change)
                }
              val newTableTempName = "__temp_table__" + prevModel.id
              val newModelTempDef = prevModel.copy(
                id = newTableTempName,
                fields = changes
                  .map(change => change.field.copy(ptype = change.newType))
                  .toSeq
              )
              val createNewTable =
                PostgresMigration(CreateModel(newModelTempDef)).run

              val stream =
                HC.stream[JsObject](
                  s"SELECT * FROM ${prevModel.id.withQuotes};",
                  HPS.set(()),
                  200
                )

              val streamIO = stream.compile.toVector

              val dropPrevTable = PostgresMigration(DeleteModel(prevModel)).run
              val renameNewTable = PostgresMigration(
                RenameModel(newTableTempName, prevModel.id)
              ).run

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
              val result: Option[ConnectionIO[Unit]] = ???
              result
            }
            case step =>
              step.renderSQL.map(
                sql => Fragment(sql, Nil).update.run.map(_ => ())
              )
          }

        effects.sequence
          .map(_.sequence)
          .sequence
          .map(_ => ())
      }
    }
  }

  private[postgres] def renderSQL: Option[String] = {
    val prefix = "CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";\n\n"
    val query = steps
      .map(_.renderSQL)
      .sequence
      .map(_.mkString("\n\n"))
    query.map(prefix + _)
  }
}

object PostgresMigration {
  def apply(
      steps: Iterable[MigrationStep]
  )(implicit syntaxTree: SyntaxTree): PostgresMigration =
    steps
      .map(PostgresMigration(_))
      .foldLeft(PostgresMigration(Vector.empty[SQLMigrationStep])(syntaxTree))(
        (acc, migration) =>
          PostgresMigration(acc.unorderedSteps ++ migration.unorderedSteps)(
            syntaxTree
          )
      )

  def apply(
      step: MigrationStep
  )(implicit syntaxTree: SyntaxTree): PostgresMigration = step match {
    case CreateModel(model) => {
      val createTableStatement = CreateTable(model.id, Vector.empty)
      val addColumnStatements = model.fields.flatMap { field =>
        PostgresMigration(AddField(field, model)).steps
      }
      PostgresMigration(
        Vector(Vector(createTableStatement), addColumnStatements).flatten
      )
    }
    case RenameModel(modelId, newId) =>
      PostgresMigration(Vector(RenameTable(modelId, newId)))
    case DeleteModel(model) =>
      PostgresMigration(Vector(DropTable(model.id)))
    case UndeleteModel(model) => PostgresMigration(CreateModel(model))
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
      PostgresMigration(AddField(field, model))
    case ChangeManyFieldTypes(prevModel, changes) =>
      PostgresMigration(Vector(AlterManyFieldTypes(prevModel, changes)))
  }
}
