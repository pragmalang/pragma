package running.storage.postgres

import doobie.util.fragment.Fragment
import doobie.implicits._
import doobie._

import cats.implicits._

import pragma.domain.DomainImplicits._

import utils._
import running.storage._
import SQLMigrationStep._

import pragma.domain._
import cats.effect._
import cats._
import running.PFunctionExecutor
import pragma.domain.utils.InternalException

import PostgresMigration.modelReferences

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
    * - The following steps are of the same priority:
    *   - Drop NOT NULL constraint
    *   - Drop UNIQUE constraint
    *   - Create UNIQUE constraint
    *   - Drop any constraint
    *   - Drop default value, Move PK
    * - Change type of a column
    * - Add a default value
    * - Rename table
    */
  private def stepPriority(step: SQLMigrationStep): Int = step match {
    case _: DropTable => 1
    case CreateTable(_, Vector(sourceCol, _)) if sourceCol.name.startsWith("source_") =>
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
        case None    => 9
      }
    case AlterTable(_, AlterTableAction.DropNotNullConstraint(_)) => 6
    case AlterTable(_, AlterTableAction.DropUnique(_))            => 6
    case AlterTable(_, AlterTableAction.MakeUnique(_))            => 6
    case AlterTable(_, AlterTableAction.DropConstraint(_))        => 6
    case AlterTable(_, AlterTableAction.AddDefault(_, _))         => 8
    case AlterTable(_, AlterTableAction.ChangeType(_, _))         => 7
    case AlterTable(_, AlterTableAction.DropDefault(_))           => 6
    case MovePrimaryKey(_, _)                                     => 6
    case _: AlterManyFieldTypes                                   => 0
    case AlterTable(_, _: AlterTableAction.RenameColumn)          => 0
    case AlterTable(_, _: AlterTableAction.DropColumn)            => 0
  }

  lazy val sqlSteps: Vector[SQLMigrationStep] =
    unorderedSQLSteps.sortBy(stepPriority)

  def run(transactor: Transactor[M]): M[Unit] =
    sqlSteps.traverse_ {
      case AlterManyFieldTypes(prevModel, changes) =>
        changes.traverse_ { change =>
          val prevType = change.field.ptype
          val newType = change.newType
          if (`Field type has changed`.`from A to A?`(prevType, newType)) {
            Fragment(
              AlterTable(
                prevModel.id,
                AlterTableAction.DropNotNullConstraint(change.field.id)
              ).renderSQL,
              Nil
            ).update.run.transact(transactor).void
          } else if (
            `Field type has changed`.`from A to [A]`(prevType, newType) |
              `Field type has changed`.`from A? to [A]`(prevType, newType)
          ) {
            val metadata = new ArrayFieldTableMetaData(prevModel, change.field)
            val primaryCol = prevModel.primaryField.id
            val colName = change.field.id
            val tableName = prevModel.id
            val createArrayTable = {
              val createTableStep = createArrayFieldTable(
                currentSyntaxTree.modelsById(prevModel.id),
                change.field,
                currentSyntaxTree
              ) match {
                case Some(value) => value.pure[M]
                case None =>
                  new InternalException(
                    s"Field $tableName.$colName was passed to `PostgresMigration#render` as an array field"
                  ).raiseError[M, CreateTable]
              }
              createTableStep.flatMap { createTableStep =>
                Fragment(
                  createTableStep.renderSQL,
                  Nil
                ).update.run.transact(transactor).void
              }
            }

            val srcCol = metadata.sourceColumnName
            val targetCol = metadata.targetColumnName
            val arrTableName = metadata.tableName

            val moveColumnValuesToArrayTable =
              sql"""
              INSERT INTO ${arrTableName.withQuotes}(${srcCol.withQuotes}, ${targetCol.withQuotes}) 
              SELECT ${primaryCol.withQuotes}, ${colName.withQuotes} FROM ${tableName.withQuotes}
              WHERE ${tableName.withQuotes}.${colName.withQuotes} IS NOT null;
              """.update.run.transact(transactor).void

            val dropColumn =
              Fragment(
                AlterTable(tableName, AlterTableAction.DropColumn(colName)).renderSQL,
                Nil
              ).update.run.transact(transactor).void

            createArrayTable *>
              moveColumnValuesToArrayTable *>
              dropColumn
          } else
            new InternalException(
              "Field type changes requiring type transformers are not implemented yet"
            ).raiseError[M, Unit]
        }
      case MovePrimaryKey(model, to) => {

        val references = modelReferences(model.id)

        val dropRefs = references.flatMap { fks =>
          val query = fks
            .map { fk =>
              AlterTable(
                fk.tableName,
                AlterTableAction.DropConstraint(fk.constraintName)
              ).renderSQL
            }
            .mkString("\n")
          Fragment(query, Nil).update.run.void
        }

        val dropOldPkConstraint =
          Fragment(
            AlterTable(
              model.id,
              AlterTableAction.DropConstraint(s"${model.id}_pkey")
            ).renderSQL,
            Nil
          ).update.run.void

        val createNewPkConstraint =
          sql"""
          ALTER TABLE ${model.id.withQuotes} ADD PRIMARY KEY (${to.id.withQuotes});
          """.update.run.void

        val createRefs = references.flatMap { fks =>
          val query = fks
            .map { fk =>
              AlterTable(
                fk.tableName,
                AlterTableAction.AddForeignKey(fk.foreignTableName, to.id, fk.columnName)
              ).renderSQL
            }
            .mkString("\n")
          Fragment(query, Nil).update.run.void
        }

        val query = for {
          _ <- dropRefs
          _ <- dropOldPkConstraint
          _ <- createNewPkConstraint
          _ <- createRefs
        } yield ()

        query.transact(transactor)
      }
      case step: DirectSQLMigrationStep =>
        Fragment(step.renderSQL, Nil).update.run
          .transact(transactor)
          .void
    }

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
            case PReference(id) if currentSyntaxTree.modelsById.get(id).isDefined =>
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
      case AddDirective(prevModel, prevField, currrentField, _) => {
        if (currrentField.isPrimary)
          MovePrimaryKey(
            prevModel,
            prevField
          ).pure[Vector]
        else if (currrentField.isUnique)
          AlterTable(prevModel.id, AlterTableAction.MakeUnique(currrentField.id))
            .pure[Vector]
        else if (currrentField.isUUID)
          Vector(
            AlterTable(
              prevModel.id,
              AlterTableAction.ChangeType(currrentField.id, PostgresType.UUID)
            ),
            AlterTable(
              prevModel.id,
              AlterTableAction.AddDefault(currrentField.id, "uuid_generate_v4 ()")
            )
          )
        else if (currrentField.isAutoIncrement)
          AlterTable(
            prevModel.id,
            AlterTableAction.ChangeType(currrentField.id, PostgresType.SERIAL8)
          ).pure[Vector]
        else Vector.empty
      }
      case DeleteDirective(prevModel, prevField, currrentField, _) => {
        if (prevField.isPrimary)
          MovePrimaryKey(
            prevModel,
            currentSyntaxTree.modelsById(prevModel.id).primaryField
          ).pure[Vector]
        else if (prevField.isUnique)
          AlterTable(prevModel.id, AlterTableAction.DropUnique(currrentField.id))
            .pure[Vector]
        else if (prevField.isUUID)
          Vector(
            AlterTable(
              prevModel.id,
              AlterTableAction.DropDefault(currrentField.id)
            ),
            AlterTable(
              prevModel.id,
              AlterTableAction.ChangeType(currrentField.id, PostgresType.TEXT)
            )
          )
        else if (prevField.isAutoIncrement)
          AlterTable(
            prevModel.id,
            AlterTableAction.ChangeType(currrentField.id, PostgresType.INT8)
          ).pure[Vector]
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
