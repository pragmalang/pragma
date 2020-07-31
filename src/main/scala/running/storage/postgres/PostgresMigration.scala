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
import scala.util.Success
import scala.util.Failure
import spray.json.JsValue
import scala.util.Try
import domain.utils.typeCheckJson
import cats.Monad

case class PostgresMigration(
    unorderedSteps: Vector[SQLMigrationStep],
    prevSyntaxTree: SyntaxTree,
    currentSyntaxTree: SyntaxTree
) {
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
              if currentSyntaxTree.modelsById.get(statement.name).isDefined =>
            1
          case (statement: CreateTable, _)
              if !currentSyntaxTree.modelsById.get(statement.name).isDefined =>
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
    // val queryEngine = new PostgresQueryEngine(transactor, st)
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
                PostgresMigration(
                  CreateModel(newModelTempDef),
                  prevSyntaxTree,
                  currentSyntaxTree
                ).run

              val stream =
                HC.stream[JsObject](
                    s"SELECT * FROM ${prevModel.id.withQuotes};",
                    HPS.set(()),
                    200
                  )
                  .map { row =>
                    val transformedColumns = changes
                      .map { change =>
                        change.transformer match {
                          case Some(transformer) =>
                            change.field.id -> transformer(
                              row.fields(change.field.id)
                            )
                          case None =>
                            change.field.id -> Success(
                              row.fields(change.field.id)
                            )
                        }
                      }
                      .foldLeft(Try(Map.empty[String, JsValue])) {
                        case (acc, transformedValue) =>
                          transformedValue._2 match {
                            case Failure(exception) =>
                              Failure(exception)
                            case Success(value) =>
                              acc.map(_ + (transformedValue._1 -> value))
                          }
                      }
                    transformedColumns.map(
                      cols => row.copy(fields = row.fields.++(cols))
                    )
                  }
                  .flatMap {
                    case Failure(exception) =>
                      fs2.Stream.raiseError[ConnectionIO](exception)
                    case Success(value) => fs2.Stream(value)
                  }
                  .flatMap { row =>
                    val typeCheckingResult =
                      typeCheckJson(newModelTempDef, currentSyntaxTree)(row)
                    typeCheckingResult match {
                      case Failure(exception) =>
                        fs2.Stream.raiseError[ConnectionIO](exception)
                      case Success(_) => fs2.Stream(row)
                    }
                  }
                  .flatMap { row =>
                    // insert the row
                    ???
                  }

              val streamIO = stream.compile.toVector

              val dropPrevTable = PostgresMigration(
                DeleteModel(prevModel),
                prevSyntaxTree,
                currentSyntaxTree
              ).run
              val renameNewTable = PostgresMigration(
                RenameModel(newTableTempName, prevModel.id),
                prevSyntaxTree,
                currentSyntaxTree
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
      steps: Iterable[MigrationStep],
      prevSyntaxTree: SyntaxTree,
      currentSyntaxTree: SyntaxTree
  ): PostgresMigration =
    steps
      .map(PostgresMigration(_, prevSyntaxTree, currentSyntaxTree))
      .foldLeft(
        PostgresMigration(
          Vector.empty[SQLMigrationStep],
          prevSyntaxTree,
          currentSyntaxTree
        )
      )(
        (acc, migration) =>
          PostgresMigration(
            acc.unorderedSteps ++ migration.unorderedSteps,
            prevSyntaxTree,
            currentSyntaxTree
          )
      )

  def apply(
      step: MigrationStep,
      prevSyntaxTree: SyntaxTree,
      currentSyntaxTree: SyntaxTree
  ): PostgresMigration = step match {
    case CreateModel(model) => {
      val createTableStatement = CreateTable(model.id, Vector.empty)
      val addColumnStatements = model.fields.flatMap { field =>
        PostgresMigration(
          AddField(field, model),
          prevSyntaxTree,
          currentSyntaxTree
        ).steps
      }
      PostgresMigration(
        Vector(Vector(createTableStatement), addColumnStatements).flatten,
        prevSyntaxTree,
        currentSyntaxTree
      )
    }
    case RenameModel(modelId, newId) =>
      PostgresMigration(
        Vector(RenameTable(modelId, newId)),
        prevSyntaxTree,
        currentSyntaxTree
      )
    case DeleteModel(model) =>
      PostgresMigration(
        Vector(DropTable(model.id)),
        prevSyntaxTree,
        currentSyntaxTree
      )
    case UndeleteModel(model) =>
      PostgresMigration(CreateModel(model), prevSyntaxTree, currentSyntaxTree)
    case AddField(field, model) => {
      val g: Option[Either[CreateTable, ForeignKey]] =
        field.ptype match {
          case POption(PArray(_)) | PArray(_) => {
            val innerModel = field.ptype match {
              case PArray(model: PModel) => Some(model)
              case PArray(PReference(id)) =>
                currentSyntaxTree.modelsById.get(id)
              case POption(PArray(model: PModel)) => Some(model)
              case POption(PArray(PReference(id))) =>
                currentSyntaxTree.modelsById.get(id)
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
                  ).get,
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
              if currentSyntaxTree.modelsById.get(id).isDefined =>
            Some(
              Right(ForeignKey(id, currentSyntaxTree.modelsById(id).primaryField.id))
            )
          case PReference(id) if currentSyntaxTree.modelsById.get(id).isDefined =>
            Some(
              Right(ForeignKey(id, currentSyntaxTree.modelsById(id).primaryField.id))
            )
          case _ => None
        }
      val alterTableStatement = fieldPostgresType(field)(currentSyntaxTree).map {
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
            },
            prevSyntaxTree,
            currentSyntaxTree
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
            },
            prevSyntaxTree,
            currentSyntaxTree
          )
      }
    }
    case RenameField(fieldId, newId, model) =>
      PostgresMigration(
        Vector(
          AlterTable(model.id, AlterTableAction.RenameColumn(fieldId, newId))
        ),
        prevSyntaxTree,
        currentSyntaxTree
      )
    case DeleteField(field, model) =>
      PostgresMigration(
        Vector(
          AlterTable(model.id, AlterTableAction.DropColumn(field.id, true))
        ),
        prevSyntaxTree,
        currentSyntaxTree
      )
    case UndeleteField(field, model) =>
      PostgresMigration(
        AddField(field, model),
        prevSyntaxTree,
        currentSyntaxTree
      )
    case ChangeManyFieldTypes(prevModel, changes) =>
      PostgresMigration(
        Vector(AlterManyFieldTypes(prevModel, changes)),
        prevSyntaxTree,
        currentSyntaxTree
      )
  }
}
