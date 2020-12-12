package running.storage.postgres

import AlterTableAction._
import pragma.domain._, DomainImplicits._
import OnDeleteAction._
import running.storage._

sealed trait SQLMigrationStep

object SQLMigrationStep {
  sealed trait DirectSQLMigrationStep extends SQLMigrationStep {
    def renderSQL: String = this match {
      case CreateTable(name, columns) => {
        val prefix = s"CREATE TABLE IF NOT EXISTS ${name.withQuotes}"
        val cols = "(\n" + columns.map(_.render).mkString(",\n") + ");\n"
        prefix + cols
      }
      case RenameTable(name, newName) =>
        s"ALTER TABLE ${name.withQuotes} RENAME TO ${newName.withQuotes};"
      case DropTable(name) => s"DROP TABLE IF EXISTS ${name.withQuotes};"
      case AlterTable(tableName, action) =>
        action match {
          case AddColumn(definition) =>
            s"ALTER TABLE ${tableName.withQuotes} ADD COLUMN ${definition.render};"
          case DropColumn(name, ifExists) => {
            val ifExistsStr = if (ifExists) "IF EXISTS" else ""
            s"ALTER TABLE ${tableName.withQuotes} DROP COLUMN $ifExistsStr ${name.withQuotes};"
          }
          case RenameColumn(name, newName) =>
            s"ALTER TABLE ${tableName.withQuotes} RENAME COLUMN ${name.withQuotes} TO ${newName.withQuotes};"

          case AddForeignKey(otherTableName, otherColumnName, thisColumnName) =>
            s"ALTER TABLE ${tableName.withQuotes} ADD FOREIGN KEY (${thisColumnName.withQuotes}) REFERENCES ${otherTableName.withQuotes}(${otherColumnName.withQuotes});"
          case DropNotNullConstraint(colName) =>
            s"ALTER TABLE ${tableName.withQuotes} ALTER COLUMN ${colName.withQuotes} DROP NOT NULL;"
          case MakeUnique(colName) =>
            s"ALTER TABLE ${tableName.withQuotes} ADD CONSTRAINT ${colName}_unique_constraint UNIQUE ${colName.withQuotes};"
          case DropUnique(colName) =>
            s"ALTER TABLE ${tableName.withQuotes} DROP CONSTRAINT ${colName}_unique_constraint;"
          case ChangeType(colName, colType) =>
            s"ALTER TABLE ${tableName.withQuotes} ALTER COLUMN ${colName.withQuotes} SET TYPE DATA ${colType.name};"
          case DropDefault(colName) =>
            s"ALTER TABLE ${tableName.withQuotes} ALTER COLUMN ${colName.withQuotes} DROP DEFAULT;"
          case AddDefault(colName, value) =>
            s"ALTER TABLE ${tableName.withQuotes} ALTER COLUMN ${colName.withQuotes} SET DEFAULT $value;"
          case DropConstraint(constraintName) =>
            s"ALTER TABLE ${tableName.withQuotes} DROP CONSTRAINT ${constraintName.withQuotes};"
        }
    }
  }
  case class CreateTable(
      name: String,
      columns: Vector[ColumnDefinition] = Vector.empty
  ) extends DirectSQLMigrationStep

  case class MovePrimaryKey(model: PModel, to: PModelField)
      extends SQLMigrationStep

  case class AlterTable(tableName: String, action: AlterTableAction)
      extends DirectSQLMigrationStep
  case class RenameTable(name: String, newName: String) extends DirectSQLMigrationStep
  case class DropTable(name: String) extends DirectSQLMigrationStep
  case class AlterManyFieldTypes(
      prevModel: PModel,
      changes: Vector[ChangeFieldType]
  ) extends SQLMigrationStep
}

sealed trait AlterTableAction
object AlterTableAction {
  case class AddColumn(definition: ColumnDefinition) extends AlterTableAction
  case class DropColumn(name: String, ifExists: Boolean = true) extends AlterTableAction
  case class RenameColumn(name: String, newName: String) extends AlterTableAction
  case class AddForeignKey(
      otherTableName: String,
      otherColumnName: String,
      thisColumnName: String
  ) extends AlterTableAction

  case class DropConstraint(constraintName: String) extends AlterTableAction

  case class DropNotNullConstraint(colName: String) extends AlterTableAction

  case class MakeUnique(colName: String) extends AlterTableAction
  case class DropUnique(colName: String) extends AlterTableAction
  case class ChangeType(colName: String, colType: PostgresType) extends AlterTableAction
  case class DropDefault(colName: String) extends AlterTableAction
  case class AddDefault(colName: String, value: String) extends AlterTableAction
}

case class ColumnDefinition(
    name: String,
    dataType: PostgresType,
    isNotNull: Boolean,
    isUnique: Boolean,
    isPrimaryKey: Boolean,
    isAutoIncrement: Boolean,
    isUUID: Boolean,
    foreignKey: Option[ForeignKey]
) {
  def render = {
    val colPrefix = s"${name.withQuotes} ${dataType.name}"
    val notNull = if (isNotNull) " NOT NULL" else ""
    val unique = if (isUnique) " UNIQUE" else ""
    val uuid = if (isUUID) " DEFAULT uuid_generate_v4 ()" else ""
    val primaryKey = if (isPrimaryKey) " PRIMARY KEY" else ""
    val autoIncrement = ""
    val fk = foreignKey match {
      case Some(fk) => {
        val onDeleteCascade = fk.onDelete match {
          case Cascade  => "ON DELETE CASCADE"
          case SetNull  => "ON DELETE RESTRICT"
          case Restrict => "ON DELETE SET NULL"
          case Default  => ""
        }
        s" REFERENCES ${fk.otherTableName.withQuotes}(${fk.otherColumnName.withQuotes}) ${onDeleteCascade} ON UPDATE CASCADE"
      }
      case None => ""
    }

    colPrefix + notNull + unique + uuid + primaryKey + autoIncrement + fk
  }
}

case class ForeignKey(
    otherTableName: String,
    otherColumnName: String,
    onDelete: OnDeleteAction = OnDeleteAction.Restrict
)

sealed trait OnDeleteAction
object OnDeleteAction {
  case object Default extends OnDeleteAction
  case object Cascade extends OnDeleteAction
  case object SetNull extends OnDeleteAction
  case object Restrict extends OnDeleteAction
}

sealed trait PostgresType {
  import PostgresType._
  def name: String = this match {
    case ANY     => "ANY"
    case UUID    => "UUID"
    case SERIAL8 => "SERIAL8"
    case TEXT    => "TEXT"
    case INT8    => "INT8"
    case FLOAT8  => "FLOAT8"
    case BOOL    => "BOOL"
    case DATE    => "DATE"
  }
}
object PostgresType {
  case object ANY extends PostgresType
  case object UUID extends PostgresType
  case object SERIAL8 extends PostgresType
  case object TEXT extends PostgresType
  case object INT8 extends PostgresType
  case object FLOAT8 extends PostgresType
  case object BOOL extends PostgresType
  case object DATE extends PostgresType
}
