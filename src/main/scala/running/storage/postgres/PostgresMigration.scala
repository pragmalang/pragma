package running.storage.postgres

import doobie.util.fragment.Fragment
import doobie.implicits._
import doobie._

import cats.implicits._

import domain.DomainImplicits._

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
import cats.effect.Bracket
import cats.effect.IO
import cats.effect.ContextShift

case class PostgresMigration(
    private val unorderedSteps: Vector[MigrationStep],
    private val prevSyntaxTree: SyntaxTree,
    private val currentSyntaxTree: SyntaxTree
) {

  lazy val unorderedSQLSteps =
    unorderedSteps.flatMap(fromMigrationStepToSqlMigrationSteps)

  lazy val sqlSteps: Vector[SQLMigrationStep] = {
    val dependencyGraph = ModelsDependencyGraph(currentSyntaxTree)
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
          case (DropTable(a), DropTable(b))
              if dependencyGraph.depsOf(a).contains(b) =>
            1
          case (_, _) => 0
        }
    }
    unorderedSQLSteps.sortWith((x, y) => sqlMigrationStepOrdering.gt(x, y))
  }

  def run(
      transactor: Transactor[IO]
  )(
      implicit bracket: Bracket[IO, Throwable],
      cs: ContextShift[IO]
  ): ConnectionIO[Unit] = {
    val queryEngine = new PostgresQueryEngine(transactor, prevSyntaxTree)

    renderSQL match {
      case Some(sql) =>
        Fragment(sql, Nil).update.run.map(_ => ())
      case None => {
        val effectfulSteps: Vector[ConnectionIO[Unit]] = sqlSteps
          .map {
            case AlterManyFieldTypes(prevModel, changes) => {
              val newTableTempName = "__temp_table__" + prevModel.id
              val newModelTempDef = prevModel.copy(
                id = newTableTempName,
                fields = changes
                  .map(change => change.field.copy(ptype = change.newType))
                  .toSeq
              )

              // Create the new table with a temp name:
              val createNewTable =
                PostgresMigration(
                  CreateModel(newModelTempDef),
                  prevSyntaxTree,
                  currentSyntaxTree
                ).run(transactor)

              val stream =
                /**
                  *  Load rows of prev table as a stream in memory
                  */
                HC.stream[JsObject](
                    s"SELECT * FROM ${prevModel.id.withQuotes};",
                    HPS.set(()),
                    200
                  )
                  .map { row =>
                    /**
                      * Pass the data in each type-changed column
                      * to the correct type transformer if any
                      */
                    val transformedColumns = changes
                      .collect { change =>
                        change.transformer match {
                          case Some(transformer) =>
                            change.field.id -> transformer(
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
                      cols => row.copy(fields = row.fields ++ cols)
                    )
                  }
                  .flatMap {
                    case Failure(exception) =>
                      fs2.Stream.raiseError[ConnectionIO](exception)
                    case Success(value) => fs2.Stream(value)
                  }
                  .flatMap { row =>
                    /**
                      * Type check the value/s returned from the transformer
                      */
                    val typeCheckingResult =
                      typeCheckJson(newModelTempDef, currentSyntaxTree)(row)
                    typeCheckingResult match {
                      case Failure(exception) =>
                        fs2.Stream.raiseError[ConnectionIO](exception)
                      case Success(_) => fs2.Stream(row)
                    }
                  }
                  .map { row =>
                    /**
                      * Try re-inserting this row in the new table
                      */
                    val insertQuery = queryEngine.createOneRecord(
                      newModelTempDef,
                      row,
                      Vector.empty
                    )
                    insertQuery.void
                  }

              val transformFieldValuesAndMoveToNewTable: ConnectionIO[Unit] =
                stream.compile.toList.map(_.head).flatten

              val dropPrevTable = PostgresMigration(
                DeleteModel(prevModel),
                prevSyntaxTree,
                currentSyntaxTree
              ).run(transactor)
              val renameNewTable = PostgresMigration(
                RenameModel(newTableTempName, prevModel.id),
                prevSyntaxTree,
                currentSyntaxTree
              ).run(transactor)

              for {
                _ <- createNewTable
                _ <- transformFieldValuesAndMoveToNewTable
                _ <- dropPrevTable
                _ <- renameNewTable
              } yield ()
            }
            case step: DirectSQLMigrationStep =>
              Fragment(step.renderSQL, Nil).update.run.map(_ => ())
          }

        effectfulSteps.sequence.map(_ => ())
      }
    }
  }

  private[postgres] lazy val renderSQL: Option[String] = {
    val prefix = "CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";\n\n"
    val query = sqlSteps
      .map {
        case step: DirectSQLMigrationStep => Some(step.renderSQL)
        case _                            => None
      }
      .sequence
      .map(_.mkString("\n\n"))
    query.map(prefix + _)
  }

  private def fromMigrationStepToSqlMigrationSteps(
      migrationStep: MigrationStep
  ): Vector[SQLMigrationStep] =
    migrationStep match {
      case CreateModel(model) => {
        val createTableStatement = CreateTable(model.id, Vector.empty)
        val addColumnStatements =
          model.fields
            .flatMap(
              field =>
                fromMigrationStepToSqlMigrationSteps(AddField(field, model))
            )
            .toVector
        Vector(Vector(createTableStatement), addColumnStatements).flatten
      }
      case RenameModel(modelId, newId) =>
        Vector(RenameTable(modelId, newId))
      case DeleteModel(model) => {
        val dropModelTable = DropTable(model.id)
        val dropJoinTables = model.fields
          .collect { f =>
            f.ptype match {
              case PArray(PReference(_))          => model.id + "_" + f.id
              case POption(PArray(PReference(_))) => model.id + "_" + f.id
            }
          }
          .map(DropTable(_))
          .toVector
        dropJoinTables :+ dropModelTable
      }
      case UndeleteModel(model) =>
        fromMigrationStepToSqlMigrationSteps(CreateModel(model))
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
                Right(
                  ForeignKey(
                    id,
                    currentSyntaxTree.modelsById(id).primaryField.id
                  )
                )
              )
            case PReference(id)
                if currentSyntaxTree.modelsById.get(id).isDefined =>
              Some(
                Right(
                  ForeignKey(
                    id,
                    currentSyntaxTree.modelsById(id).primaryField.id
                  )
                )
              )
            case _ => None
          }
        val alterTableStatement =
          fieldPostgresType(field)(currentSyntaxTree).map { postgresType =>
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
            g match {
              case Some(value) =>
                value match {
                  case Left(createArrayTableStatement) =>
                    Vector(createArrayTableStatement)
                  case Right(_) => Vector.empty
                }
              case None => Vector.empty
            }
          case Some(alterTableStatement) =>
            g match {
              case Some(value) =>
                value match {
                  case Left(createArrayTableStatement) =>
                    Vector(alterTableStatement, createArrayTableStatement)
                  case Right(_) => Vector(alterTableStatement)
                }
              case None => Vector(alterTableStatement)
            }
        }
      }
      case RenameField(fieldId, newId, model) =>
        Vector(
          AlterTable(model.id, AlterTableAction.RenameColumn(fieldId, newId))
        )
      case DeleteField(field, model) =>
        Vector(
          AlterTable(model.id, AlterTableAction.DropColumn(field.id, true))
        )
      case UndeleteField(field, model) =>
        fromMigrationStepToSqlMigrationSteps(AddField(field, model))
      case ChangeManyFieldTypes(prevModel, _, changes) =>
        Vector(AlterManyFieldTypes(prevModel, changes))
    }
}

object PostgresMigration {
  def apply(
      step: MigrationStep,
      prevSyntaxTree: SyntaxTree,
      currentSyntaxTree: SyntaxTree
  ): PostgresMigration =
    PostgresMigration(Vector(step), prevSyntaxTree, currentSyntaxTree)
}

case class ModelsDependencyGraph(st: SyntaxTree) {

  val pairs: List[(PModel, PType)] = {
    for {
      model <- st.models.toList
      field <- model.fields
    } yield
      field.ptype match {
        case t @ PReference(_)                  => Some((model, t))
        case t @ PArray(PReference(_))          => Some((model, t))
        case t @ POption(PReference(_))         => Some((model, t))
        case t @ POption(PArray(PReference(_))) => Some((model, t))
        case _                                  => None
      }
  } collect {
    case Some(value) => value
  }

  def depsOf(modelId: String) = pairs collect {
    case (model, PReference(ref)) if modelId == model.id                  => ref
    case (model, PArray(PReference(ref))) if modelId == model.id          => ref
    case (model, POption(PReference(ref))) if modelId == model.id         => ref
    case (model, POption(PArray(PReference(ref)))) if modelId == model.id => ref
  }
}
