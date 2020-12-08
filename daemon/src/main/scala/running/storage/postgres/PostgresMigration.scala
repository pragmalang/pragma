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
    unorderedSteps.flatMap(fromMigrationStep)

  /** - Change column type, Rename column, Drop column
    * - Drop table, Rename table whose old name is the same as a newly created table's name
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
    case RenameTable(oldName, _) =>
      unorderedSQLSteps.find {
        case createTable: CreateTable if createTable.name == oldName => true
        case _                                                       => false
      } match {
        case Some(_) => 1
        case None    => 6
      }
    case _: AlterManyFieldTypes                          => 0
    case AlterTable(_, _: AlterTableAction.RenameColumn) => 0
    case AlterTable(_, _: AlterTableAction.DropColumn)   => 0
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
  ): M[Unit] =
    sqlSteps.traverse {
      case AlterManyFieldTypes(prevModel, changes) => {
        val newTableTempName = "__temp__" + prevModel.id
        val newFields = prevModel.fields.map { field =>
          changes.find(_.field.id == field.id) match {
            case Some(change) => field.copy(ptype = change.newType)
            case None         => field
          }
        }

        val newTableModelDef =
          prevModel.copy(
            id = newTableTempName,
            fields = newFields
          )

        // Create the new table with a temp name:
        val createNewTableWithTempName =
          PostgresMigration[M](
            CreateModel(newTableModelDef),
            prevSyntaxTree,
            currentSyntaxTree,
            queryEngine,
            funcExecutor
          )

        val stream = for {
          row <- HC.stream[JsObject](
            s"SELECT * FROM ${prevModel.id.withQuotes};",
            HPS.set(()),
            200
          )

          /** Pass the data in each type-changed column
            * to the correct type transformer if any
            */
          transformedRow = changes
            .collect { change =>
              val transformerArg = row.fields(change.field.id) match {
                case obj: JsObject => obj
                case value         => JsObject("arg" -> value)
              }
              change.transformer match {
                case Some(transformer) =>
                  funcExecutor
                    .execute(transformer, transformerArg)
                    .map(result => change -> result)
                    .widen[(ChangeFieldType, JsValue)]

                /**
                  * No need for a transformation function, the current value,
                  * will be the first element of the array.
                  */
                case None
                    if `Field type has changed` `from A to [A]` (change.field.ptype, change.newType) =>
                  (change -> JsArray(row.fields(change.field.id)))
                    .pure[M]
                    .widen[(ChangeFieldType, JsValue)]

                /**
                  * No need for a transformation function, the current value, if not null,
                  * will be the first element in the array, and if it's null then the array
                  * is empty.
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
            .map { cols =>
              row.copy(
                fields = row.fields ++ cols
                  .map(col => col._1.field.id -> col._2)
              )
            }

          /**
            * Type check the value/s returned from the transformer
            */
          typeCheckingResult = typeCheckJson(
            newTableModelDef,
            currentSyntaxTree
          )(row)

          _ = queryEngine.createOneRecord(
            newTableModelDef,
            typeCheckingResult.get.asJsObject,
            Vector.empty
          )
        } yield ()

        val transformFieldValuesAndMoveToNewTable = stream.compile.toVector
          .transact(transactor)
          .void

        val dropPrevTable = PostgresMigration[M](
          DeleteModel(prevModel),
          prevSyntaxTree,
          currentSyntaxTree,
          queryEngine,
          funcExecutor
        )
        val renameNewTable = PostgresMigration[M](
          RenameModel(newTableTempName, prevModel.id),
          prevSyntaxTree,
          currentSyntaxTree,
          queryEngine,
          funcExecutor
        )

        createNewTableWithTempName.run(transactor) *>
          transformFieldValuesAndMoveToNewTable *>
          dropPrevTable.run(transactor) *>
          renameNewTable.run(transactor).void
      }
      case step: DirectSQLMigrationStep =>
        Fragment(step.renderSQL, Nil).update.run
          .transact(transactor)
          .void
    }.void

  private def fromMigrationStep(
      migrationStep: MigrationStep
  ): Vector[SQLMigrationStep] =
    migrationStep match {
      case CreateModel(model) => {
        val createTableStatement = CreateTable(model.id, Vector.empty)
        val addColumnStatements =
          model.fields.flatMap { field =>
            fromMigrationStep(AddField(field, model))
          }.toVector
        Vector(Vector(createTableStatement), addColumnStatements).flatten
      }
      case RenameModel(modelId, newId) =>
        Vector(RenameTable(modelId, newId))
      case DeleteModel(model) => Vector(DropTable(model.id))
      case UndeleteModel(model) =>
        fromMigrationStep(CreateModel(model))
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
                  isUnique = field.isUnique || field.isPublicCredential,
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
        fromMigrationStep(AddField(field, model))
      case ChangeManyFieldTypes(prevModel, _, changes) =>
        Vector(AlterManyFieldTypes(prevModel, changes))
      case AddDirective(_, _, currrentField, _) => {
        if (currrentField.isPrimary) ???
        else if (currrentField.isUnique) ???
        else if (currrentField.isUUID) ???
        else if (currrentField.isAutoIncrement) ???
        else Vector.empty
      }
      case DeleteDirective(_, prevField, _, _) => {
        if (prevField.isPrimary) ???
        else if (prevField.isUnique) ???
        else if (prevField.isUUID) ???
        else if (prevField.isAutoIncrement) ???
        else Vector.empty
      }
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

  def modelReferences(
      modelId: String
  ): ConnectionIO[Vector[ForeignKeyMetaData]] =
    sql"""
      SELECT
        tc.constraint_name, 
        tc.table_schema, 
        tc.table_name, 
        kcu.column_name, 
        ccu.table_schema AS foreign_table_schema,
        ccu.table_name AS foreign_table_name,
        ccu.column_name AS foreign_column_name 
      FROM 
        information_schema.table_constraints AS tc 
      JOIN information_schema.key_column_usage AS kcu
        ON tc.constraint_name = kcu.constraint_name
        AND tc.table_schema = kcu.table_schema
      JOIN information_schema.constraint_column_usage AS ccu
        ON ccu.constraint_name = tc.constraint_name
        AND ccu.table_schema = tc.table_schema
      WHERE tc.constraint_type = 'FOREIGN KEY' AND ccu.table_name=$modelId;
      """
      .query[ForeignKeyMetaData]
      .to[Vector]
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
