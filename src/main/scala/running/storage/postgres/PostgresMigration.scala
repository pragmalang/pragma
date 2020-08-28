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

import domain._
import scala.util.Success
import scala.util.Failure
import spray.json.JsValue
import scala.util.Try
import domain.utils.typeCheckJson
import cats.effect.Bracket
import cats.effect.ContextShift
import spray.json._
import cats.Monad

case class PostgresMigration[M[_]: Monad](
    private val unorderedSteps: Vector[MigrationStep],
    private val prevSyntaxTree: SyntaxTree,
    private val currentSyntaxTree: SyntaxTree,
    private val queryEngine: PostgresQueryEngine[M]
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
          case (
              _: AlterManyFieldTypes,
              AlterTable(_, _: AlterTableAction.RenameColumn)
              ) =>
            1
          case (_: AlterManyFieldTypes, _: RenameTable) => 1
          case (_, _)                                   => 0
        }
    }
    unorderedSQLSteps.sortWith((x, y) => sqlMigrationStepOrdering.gt(x, y))
  }

  def run(
      transactor: Transactor[M]
  )(
      implicit bracket: Bracket[M, Throwable],
      cs: ContextShift[M]
  ): M[Unit] = renderSQL match {
    case Some(sql) =>
      Fragment(sql, Nil).update.run.map(_ => ()).transact(transactor)
    case None => {
      val effectfulSteps: Vector[M[Unit]] = sqlSteps
        .map {
          case AlterManyFieldTypes(prevModel, changes) => {
            val tempTableName = "__temp_table__" + prevModel.id
            val tempTableModelDef = prevModel.copy(
              id = tempTableName,
              fields = changes
                .map(change => change.field.copy(ptype = change.newType))
                .toSeq
            )

            // Create the new table with a temp name:
            val createTempTable =
              PostgresMigration[M](
                CreateModel(tempTableModelDef),
                prevSyntaxTree,
                currentSyntaxTree,
                queryEngine
              )

            val stream =
              // Load rows of prev table as a stream in memory
              HC.stream[JsObject](
                  s"SELECT * FROM ${prevModel.id.withQuotes};",
                  HPS.set(()),
                  200
                )
                .map { row =>
                  /* Pass the data in each type-changed column
                   * to the correct type transformer if any
                   */
                  val transformedColumns = changes
                    .collect { change =>
                      change.transformer match {
                        case Some(transformer) =>
                          change -> transformer(
                            row.fields(change.field.id)
                          )
                        /*
                            No need for a transformation function, the current value,
                            will be the first element of the array.
                         */
                        case None
                            if `Field type has changed` `from A to [A]` (change.field.ptype, change.newType) =>
                          change -> Success {
                            JsArray(row.fields(change.field.id))
                          }
                        /*
                            No need for a transformation function, the current value, if not null,
                            will be the first element in the array, and if it's null then the array
                            is empty.
                         */
                        case None
                            if `Field type has changed` `from A? to [A]` (change.field.ptype, change.newType) =>
                          change -> (row.fields(change.field.id) match {
                            case JsNull => Success(JsArray.empty)
                            case value  => Success(JsArray(value))
                          })
                      }
                    }
                    .foldLeft(Try(Map.empty[ChangeFieldType, JsValue])) {
                      case (acc, transformedValue) =>
                        transformedValue._2 match {
                          case Failure(exception) =>
                            Failure(exception)
                          case Success(value) =>
                            acc.map(_ + (transformedValue._1 -> value))
                        }
                    }
                  transformedColumns.map { cols =>
                    row.copy(
                      fields = row.fields ++ cols
                        .map(col => col._1.field.id -> col._2)
                    )
                  }
                }
                .flatMap {
                  case Failure(exception) =>
                    fs2.Stream.raiseError[ConnectionIO](exception)
                  case Success(value) => fs2.Stream(value)
                }
                .flatMap { row =>
                  // Type check the value/s returned from the transformer
                  val typeCheckingResult =
                    typeCheckJson(tempTableModelDef, currentSyntaxTree)(row)
                  typeCheckingResult match {
                    case Failure(exception) =>
                      fs2.Stream.raiseError[ConnectionIO](exception)
                    case Success(_) => fs2.Stream(row)
                  }
                }
                .map { row =>
                  // Try re-inserting this row in the new table
                  val insertQuery = queryEngine.createOneRecord(
                    tempTableModelDef,
                    row,
                    Vector.empty
                  )
                  insertQuery.void
                }

            val transformFieldValuesAndMoveToNewTable =
              stream.compile.toList.map(_.head).flatten

            val dropPrevTable = PostgresMigration[M](
              DeleteModel(prevModel),
              prevSyntaxTree,
              currentSyntaxTree,
              queryEngine
            )
            val renameNewTable = PostgresMigration[M](
              RenameModel(tempTableName, prevModel.id),
              prevSyntaxTree,
              currentSyntaxTree,
              queryEngine
            )

            for {
              _ <- createTempTable.run(transactor)
              _ <- transformFieldValuesAndMoveToNewTable.transact(transactor)
              _ <- dropPrevTable.run(transactor)
              _ <- renameNewTable.run(transactor)
            } yield ()
          }
          case step: DirectSQLMigrationStep =>
            Fragment(step.renderSQL, Nil).update.run
              .map(_ => ())
              .transact(transactor)
        }

      effectfulSteps.sequence.map(_ => ())
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
        val fieldCreationInstruction: Option[Either[CreateTable, ForeignKey]] =
          field.ptype match {
            case POption(PArray(_)) | PArray(_) =>
              createArrayFieldTable(model, field, currentSyntaxTree)
                .map(_.asLeft)
            case model: PModel =>
              ForeignKey(model.id, model.primaryField.id).asRight.some
            case POption(model: PModel) =>
              ForeignKey(model.id, model.primaryField.id).asRight.some
            case POption(PReference(id))
                if currentSyntaxTree.modelsById.get(id).isDefined =>
              ForeignKey(
                id,
                currentSyntaxTree.modelsById(id).primaryField.id
              ).asRight.some
            case PReference(id)
                if currentSyntaxTree.modelsById.get(id).isDefined =>
              ForeignKey(
                id,
                currentSyntaxTree.modelsById(id).primaryField.id
              ).asRight.some
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
                  isUnique = field.isUnique || field.isPublicCredential || field.isSecretCredential,
                  isPrimaryKey = field.isPrimary,
                  isAutoIncrement = field.isAutoIncrement,
                  isUUID = field.isUUID,
                  foreignKey = fieldCreationInstruction match {
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
            fieldCreationInstruction match {
              case Some(value) =>
                value match {
                  case Left(createArrayTableStatement) =>
                    Vector(createArrayTableStatement)
                  case Right(_) => Vector.empty
                }
              case None => Vector.empty
            }
          case Some(alterTableStatement) =>
            fieldCreationInstruction match {
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
  def apply[M[_]: Monad](
      step: MigrationStep,
      prevSyntaxTree: SyntaxTree,
      currentSyntaxTree: SyntaxTree,
      queryEngine: PostgresQueryEngine[M]
  ): PostgresMigration[M] =
    PostgresMigration(
      Vector(step),
      prevSyntaxTree,
      currentSyntaxTree,
      queryEngine
    )
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
