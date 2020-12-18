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

case class PostgresMigration[M[_]: Monad: Async: ConcurrentEffect](
    private val unorderedSteps: Vector[MigrationStep],
    private val prevSyntaxTree: SyntaxTree,
    private val currentSyntaxTree: SyntaxTree,
    private val queryEngine: PostgresQueryEngine[M],
    private val funcExecutor: PFunctionExecutor[M]
) {

  import PostgresMigration._

  lazy val unorderedSQLSteps =
    unorderedSteps.flatMap(fromMigrationStep)

  /** - Deferred change field type, Rename column, Drop column
    * - Drop table, Rename table whose old name is the same as a newly created table's name
    * - Create table statements
    * - Drop primary constraint
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
    * - Add a default value, make auto increment
    * - Rename table
    */
  private def stepPriority(step: SQLMigrationStep): Int = step match {
    case _: DropTable => 1
    case CreateTable(_, Vector(sourceCol, _)) if sourceCol.name.startsWith("source_") =>
      6
    case CreateTable(_, _) => 2
    case AlterTable(
          _,
          AlterTableAction.AddColumn(
            ColumnDefinition(_, _, _, _, _, _, _, Some(_))
          )
        ) =>
      5
    case DeferredAddField(_, field) if field.isReference => 5
    case AlterTable(_, _: AlterTableAction.AddColumn) =>
      4
    case DeferredAddField(_, _)                           => 4
    case AlterTable(_, _: AlterTableAction.AddForeignKey) => 5
    case RenameTable(oldName, _) =>
      unorderedSQLSteps.find {
        case createTable: CreateTable if createTable.name == oldName => true
        case _                                                       => false
      } match {
        case Some(_) => 1
        case None    => 10
      }
    case AlterTable(_, AlterTableAction.DropNotNullConstraint(_)) => 7
    case AlterTable(_, AlterTableAction.DropUnique(_))            => 7
    case AlterTable(_, AlterTableAction.MakeUnique(_))            => 7
    case AlterTable(_, AlterTableAction.DropConstraint(_))        => 7
    case AlterTable(_, AlterTableAction.AddDefault(_, _))         => 9
    case AlterTable(_, AlterTableAction.ChangeType(_, _))         => 8
    case DeferredMakeAutoIncrement(_, _)                          => 9
    case AlterTable(_, AlterTableAction.DropDefault(_))           => 7
    case DeferredMovePK(_, _)                                     => 7
    case AlterTable(_, AlterTableAction.DropPrimaryConstraint)    => 3
    case _: DeferredChangeFieldTypes                              => 0
    case AlterTable(_, _: AlterTableAction.RenameColumn)          => 0
    case AlterTable(_, _: AlterTableAction.DropColumn)            => 0
  }

  lazy val sqlSteps: Vector[SQLMigrationStep] =
    unorderedSQLSteps.sortBy(stepPriority)

  def run(
      transactor: Transactor[M],
      thereExistData: Map[String, Boolean]
  ): M[Unit] =
    run(transactor, thereExistData, sqlSteps)
  def run(
      transactor: Transactor[M],
      thereExistData: Map[String, Boolean],
      sqlSteps: Vector[SQLMigrationStep]
  ): M[Unit] =
    sqlSteps.traverse_ {
      case DeferredChangeFieldTypes(prevModel, changes) =>
        changes.traverse_ { change =>
          val prevType = change.prevField.ptype
          val newType = change.currentField.ptype
          if (`Field type has changed`.`from A to A?`(prevType, newType)) {
            Fragment(
              AlterTable(
                prevModel.id,
                AlterTableAction.DropNotNullConstraint(change.prevField.id)
              ).renderSQL,
              Nil
            ).update.run.transact(transactor).void
          } else if (
            `Field type has changed`.`from A to [A]`(prevType, newType) |
              `Field type has changed`.`from A? to [A]`(prevType, newType)
          ) {
            val metadata = new ArrayFieldTableMetaData(prevModel, change.currentField)
            val primaryCol = prevModel.primaryField.id
            val colName = change.currentField.id
            val tableName = prevModel.id
            val createArrayTable = {
              val createTableStep = createArrayFieldTable(
                currentSyntaxTree.modelsById(prevModel.id),
                change.currentField,
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
              Fragment(
                s"""
                INSERT INTO ${arrTableName.withQuotes}(${srcCol.withQuotes}, ${targetCol.withQuotes}) 
                SELECT ${primaryCol.withQuotes}, ${colName.withQuotes} FROM ${tableName.withQuotes}
                WHERE ${tableName.withQuotes}.${colName.withQuotes} IS NOT null;
                """,
                Nil
              ).update.run.transact(transactor).void

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
      case DeferredMovePK(model, to) => {

        val references = modelReferences(model.id)

        val dropRefs = dropRefsToTable(model.id)

        val dropOldPkConstraint =
          Fragment(
            AlterTable(
              model.id,
              AlterTableAction.DropPrimaryConstraint
            ).renderSQL,
            Nil
          ).update.run.void

        val createNewPkConstraint =
          Fragment(
            s"ALTER TABLE ${model.id.withQuotes} ADD PRIMARY KEY (${to.id.withQuotes});",
            Nil
          ).update.run.void

        val createRefs = references.flatMap(createRefsToTable(_, to.id))

        val query = for {
          _ <- dropRefs
          _ <- dropOldPkConstraint
          _ <- createNewPkConstraint
          _ <- createRefs
        } yield ()

        query.transact(transactor)
      }
      case DeferredAddField(model, field) => {
        val fieldCreationInstruction: Option[Either[CreateTable, ForeignKey]] =
          field.ptype match {
            case PArray(_) =>
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
        def alterTableStatement(isNotNull: Boolean) =
          fieldPostgresType(field)(currentSyntaxTree).map { postgresType =>
            AlterTable(
              model.id,
              AlterTableAction.AddColumn(
                ColumnDefinition(
                  name = field.id,
                  dataType = postgresType,
                  isNotNull = isNotNull,
                  isUnique = field.isUnique || field.isPublicCredential,
                  isPrimaryKey =
                    field.isPrimary && !prevSyntaxTree.models.exists(_ == model),
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

        def addField(isNotNull: Boolean) = alterTableStatement(isNotNull) match {
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

        val addFieldWithoutNotNull = run(transactor, thereExistData, addField(false))

        val addDefaultValueToExistingRecords =
          if (!field.isArray && !field.isReference && !field.isOptional)
            field.defaultValue.flatMap(pvalueToString) match {
              case Some(value) =>
                Fragment(
                  s"""
                UPDATE ${model.id.withQuotes} SET ${field.id.withQuotes} = $value;
                """,
                  Nil
                ).update.run.transact(transactor).void
              case None => ().pure[M]
            }
          else ().pure[M]

        val addNotNullConstraint =
          if (!field.isOptional && !field.isArray)
            Fragment(
              s"ALTER TABLE ${model.id.withQuotes} ALTER COLUMN ${field.id.withQuotes} SET NOT NULL;",
              Nil
            ).update.run.void.transact(transactor)
          else
            ().pure[M]

        if (field.isPrimary && prevSyntaxTree.models.exists(_ == model)) {
          val movePk =
            run(transactor, thereExistData, DeferredMovePK(model, field).pure[Vector])
          addFieldWithoutNotNull *> addDefaultValueToExistingRecords *> addNotNullConstraint *> movePk
        } else
          addFieldWithoutNotNull *> addDefaultValueToExistingRecords *> addNotNullConstraint
      }
      case DeferredMakeAutoIncrement(prevModel, field) => {

        val seqName = (prevModel.id + "_" + field.id + "_seq").withQuotes
        val maxValue =
          Fragment(
            s"SELECT MAX(${field.id.withQuotes})+1 FROM ${prevModel.id.withQuotes};",
            Nil
          ).query[Int].unique

        val createSequence = maxValue.flatMap { maxValue =>
          Fragment(
            s"""
            CREATE SEQUENCE IF NOT EXISTS $seqName MINVALUE $maxValue;
            """,
            Nil
          ).update.run.void
        }
        val addSequenceAsDefault = Fragment(
          AlterTable(
            prevModel.id,
            AlterTableAction.AddDefault(
              field.id,
              s"nextval($seqName)"
            )
          ).renderSQL,
          Nil
        ).update.run.void
        (createSequence *> addSequenceAsDefault).transact(transactor)
      }
      case step: DirectSQLMigrationStep =>
        {
          importUUIDExtension *>
            Fragment(step.renderSQL, Nil).update.run
        }.transact(transactor).void
    }

  private def importUUIDExtension =
    Fragment("""CREATE EXTENSION IF NOT EXISTS "uuid-ossp";""", Nil).update.run

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
      case RenameModel(modelId, newId) => {

        val prevModel = prevSyntaxTree.modelsById(modelId)
        val currentModel = currentSyntaxTree.modelsById(newId)

        val renameArrTables = currentModel.fields
          .map(f => f -> f.ptype)
          .collect {
            case (field, PArray(_)) => {
              val prevArrMeta =
                prevModel.fields.find(_.index == field.index) match {
                  case Some(prevField)
                      if prevField.id != field.id => // In case the field was renamed
                    new ArrayFieldTableMetaData(prevModel, field)
                  case Some(prevField) =>
                    new ArrayFieldTableMetaData(prevModel, prevField)
                  case None => // In case `field` is newly added or has been converted to an array in this migration
                    new ArrayFieldTableMetaData(prevModel, field)
                }
              val arrMeta = new ArrayFieldTableMetaData(currentModel, field)

              RenameTable(prevArrMeta.tableName, arrMeta.tableName)
            }
          }
          .toVector

        renameArrTables :+ RenameTable(modelId, newId)
      }
      case DeleteModel(model)     => Vector(DropTable(model.id))
      case AddField(field, model) => DeferredAddField(model, field).pure[Vector]
      case RenameField(fieldId, newId, model) => {
        val field = model.fieldsById(fieldId)
        val newField = model.fieldsById(fieldId).copy(id = newId)
        val arrFieldMeta = new ArrayFieldTableMetaData(model, field)
        val newArrFieldMeta = new ArrayFieldTableMetaData(model, newField)
        field.isArray match {
          case true =>
            AlterTable(
              arrFieldMeta.tableName,
              AlterTableAction.RenameColumn(
                arrFieldMeta.targetColumnName,
                newArrFieldMeta.targetColumnName
              )
            ).pure[Vector]
          case false =>
            AlterTable(
              model.id,
              AlterTableAction.RenameColumn(fieldId, newId)
            ).pure[Vector]
        }
      }
      case DeleteField(field, model) =>
        Vector(
          AlterTable(model.id, AlterTableAction.DropColumn(field.id, true))
        )
      case ChangeFieldTypes(prevModel, _, changes) =>
        Vector(DeferredChangeFieldTypes(prevModel, changes))
      case AddDirective(prevModel, prevField, currentField, _) => {
        if (currentField.isPrimary)
          DeferredMovePK(
            prevModel,
            prevField
          ).pure[Vector]
        else if (currentField.isUnique)
          AlterTable(prevModel.id, AlterTableAction.MakeUnique(currentField.id))
            .pure[Vector]
        else if (currentField.isUUID)
          Vector(
            AlterTable(
              prevModel.id,
              AlterTableAction.ChangeType(currentField.id, PostgresType.UUID)
            ),
            AlterTable(
              prevModel.id,
              AlterTableAction.AddDefault(currentField.id, "uuid_generate_v4 ()")
            )
          )
        else if (currentField.isAutoIncrement)
          AlterTable(
            prevModel.id,
            AlterTableAction.ChangeType(currentField.id, PostgresType.SERIAL8)
          ).pure[Vector]
        else Vector.empty
      }
      case DeleteDirective(prevModel, prevField, currentField, _) => {
        if (prevField.isPrimary)
          DeferredMovePK(
            prevModel,
            currentSyntaxTree.modelsById(prevModel.id).primaryField
          ).pure[Vector]
        else if (prevField.isUnique)
          AlterTable(prevModel.id, AlterTableAction.DropUnique(currentField.id))
            .pure[Vector]
        else if (prevField.isUUID)
          Vector(
            AlterTable(
              prevModel.id,
              AlterTableAction.DropDefault(currentField.id)
            ),
            AlterTable(
              prevModel.id,
              AlterTableAction.ChangeType(currentField.id, PostgresType.TEXT)
            )
          )
        else if (prevField.isAutoIncrement)
          AlterTable(
            prevModel.id,
            AlterTableAction.DropDefault(currentField.id)
          ).pure[Vector]
        else Vector.empty
      }
    }
}

object PostgresMigration {
  private def pvalueToString(value: PValue): Option[String] = value match {
    case PStringValue(value)    => value.some
    case PIntValue(value)       => value.toString.some
    case PFloatValue(value)     => value.toString.some
    case PBoolValue(value)      => value.toString.some
    case PDateValue(value)      => value.toString.some
    case POptionValue(value, _) => value.flatMap(pvalueToString)
    case _                      => None
  }
  private def dropRefsToTable(modelId: String) = modelReferences(modelId).flatMap { fks =>
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

  private def createRefsToTable(
      refs: Vector[ForeignKeyMetaData],
      newPrimaryFieldId: String
  ) = {
    val query =
      refs
        .map { fk =>
          AlterTable(
            fk.tableName,
            AlterTableAction
              .AddForeignKey(fk.foreignTableName, newPrimaryFieldId, fk.columnName)
          ).renderSQL
        }
        .mkString("\n")

    Fragment(query, Nil).update.run.void
  }
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
