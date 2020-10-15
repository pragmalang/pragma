package running.storage.postgres

import doobie.util.fragment.Fragment
import doobie.implicits._
import doobie._

import cats.implicits._

import pragma.domain.DomainImplicits._

import running.storage.postgres.instances._
import spray.json.JsObject

import utils._
import running.storage._
import SQLMigrationStep._

import pragma.domain._
import spray.json.JsValue
import pragma.domain.utils.typeCheckJson
import cats.effect._
import spray.json._
import cats._
import running.PFunctionExecutor

case class PostgresMigration[M[_]: Monad: Async: ConcurrentEffect](
    private val unorderedSteps: Vector[MigrationStep],
    private val prevSyntaxTree: SyntaxTree,
    private val currentSyntaxTree: SyntaxTree,
    private val queryEngine: PostgresQueryEngine[M],
    private val funcExecutor: PFunctionExecutor[M]
) {

  lazy val unorderedSQLSteps =
    unorderedSteps.flatMap(fromMigrationStepToSqlMigrationSteps)

  /**
    * - Change column type, Rename column, Drop column
    * - Drop table
    * - Create table statements
    * - Add column (primitive type)
    * - Add foreign keys to non-array tables
    * - Create array tables
    * - Rename table
    */
  private def stepPriority(step: SQLMigrationStep): Int = step match {
    case _: DropTable => 1
    case CreateTable(_, Vector(sourceCol, _))
        if sourceCol.name.startsWith("source_") =>
      5
    case CreateTable(_, _) => 2
    case AlterTable(
        _,
        AlterTableAction.AddColumn(
          ColumnDefinition(_, _, _, _, _, _, _, Some(_))
        )
        ) =>
      4
    case AlterTable(_, _: AlterTableAction.AddColumn) =>
      3
    case AlterTable(_, _: AlterTableAction.AddForeignKey) => 4
    case _: RenameTable                                   => 6
    case _: AlterManyFieldTypes                           => 0
    case AlterTable(_, _: AlterTableAction.RenameColumn)  => 0
    case AlterTable(_, _: AlterTableAction.DropColumn)    => 0
  }

  lazy val sqlSteps: Vector[SQLMigrationStep] =
    unorderedSQLSteps
      .map(step => stepPriority(step) -> step)
      .sortBy(_._1)
      .map(_._2)

  def run(
      transactor: Transactor[M]
  )(
      implicit cs: ContextShift[M]
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
                queryEngine,
                funcExecutor
              )

            val stream: fs2.Stream[ConnectionIO, M[Unit]] =
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
                    .collect { (change: ChangeFieldType) =>
                      change.transformer match {
                        case Some(transformer) =>
                          funcExecutor
                            .execute(
                              transformer,
                              row.fields(change.field.id)
                            )
                            .map(result => change -> result)
                            .widen[(ChangeFieldType, JsValue)]
                        /*
                            No need for a transformation function, the current value,
                            will be the first element of the array.
                         */
                        case None
                            if `Field type has changed` `from A to [A]` (change.field.ptype, change.newType) =>
                          (change -> JsArray(row.fields(change.field.id)))
                            .pure[M]
                            .widen[(ChangeFieldType, JsValue)]
                        /*
                            No need for a transformation function, the current value, if not null,
                            will be the first element in the array, and if it's null then the array
                            is empty.
                         */
                        case None
                            if `Field type has changed` `from A? to [A]` (change.field.ptype, change.newType) =>
                          (change -> (row.fields(change.field.id) match {
                            case JsNull => JsArray.empty
                            case value  => JsArray(value)
                          })).pure[M].widen[(ChangeFieldType, JsValue)]
                      }
                    }
                    .sequence
                    .map(_.toMap)
                  transformedColumns
                    .map { cols =>
                      row.copy(
                        fields = row.fields ++ cols
                          .map(col => col._1.field.id -> col._2)
                      )
                    }
                }
                .map { row =>
                  // Type check the value/s returned from the transformer
                  row.map { row =>
                    val typeCheckingResult =
                      typeCheckJson(tempTableModelDef, currentSyntaxTree)(row)
                    typeCheckingResult
                  }
                }
                .map { row =>
                  // Try re-inserting this row in the new table
                  row.map { row =>
                    val insertQuery = queryEngine.createOneRecord(
                      tempTableModelDef,
                      row.get.asJsObject,
                      Vector.empty
                    )
                    insertQuery.void
                  }
                }

            val transformFieldValuesAndMoveToNewTable = stream.compile.toVector
              .map(_.sequence)
              .transact(transactor)
              .flatten
              .void

            val dropPrevTable = PostgresMigration[M](
              DeleteModel(prevModel),
              prevSyntaxTree,
              currentSyntaxTree,
              queryEngine,
              funcExecutor
            )
            val renameNewTable = PostgresMigration[M](
              RenameModel(tempTableName, prevModel.id),
              prevSyntaxTree,
              currentSyntaxTree,
              queryEngine,
              funcExecutor
            )

            createTempTable.run(transactor) *>
              transformFieldValuesAndMoveToNewTable *>
              dropPrevTable.run(transactor) *>
              renameNewTable.run(transactor)
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
      case DeleteModel(model) => Vector(DropTable(model.id))
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
  def apply[M[_]: Monad: Async: ConcurrentEffect](
      step: MigrationStep,
      prevSyntaxTree: SyntaxTree,
      currentSyntaxTree: SyntaxTree,
      queryEngine: PostgresQueryEngine[M],
      funcExecutor: PFunctionExecutor[M]
  ): PostgresMigration[M] =
    PostgresMigration(
      Vector(step),
      prevSyntaxTree,
      currentSyntaxTree,
      queryEngine,
      funcExecutor
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
