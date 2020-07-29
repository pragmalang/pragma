package running.storage.postgres

import SQLMigrationStep._
import AlterTableAction._
import domain.DomainImplicits._
import OnDeleteAction._
import domain.PModel
import running.storage.ChangeFieldType

sealed trait SQLMigrationStep {
  def renderSQL: Option[String] = this match {
    case CreateTable(name, columns) => {
      val prefix = s"CREATE TABLE IF NOT EXISTS ${name.withQuotes}"
      val cols = "(\n" + columns.map(_.render).mkString(",\n") + ");\n"
      Some(prefix + cols)
    }
    case RenameTable(name, newName) =>
      Some(s"ALTER TABLE ${name.withQuotes} RENAME TO ${newName.withQuotes};")
    case DropTable(name) => Some(s"DROP TABLE IF EXISTS ${name.withQuotes};")
    case AlterTable(tableName, action) =>
      action match {
        case AddColumn(definition) =>
          Some(
            s"ALTER TABLE ${tableName.withQuotes} ADD COLUMN ${definition.render};"
          )
        case DropColumn(name, ifExists) => {
          val ifExistsStr = if (ifExists) "IF EXISTS" else ""
          Some(
            s"ALTER TABLE ${tableName.withQuotes} DROP COLUMN $ifExistsStr ${name.withQuotes};"
          )
        }
        case RenameColumn(name, newName) =>
          Some(
            s"ALTER TABLE ${tableName.withQuotes} RENAME COLUMN ${name.withQuotes} TO ${newName.withQuotes};"
          )
        case AddForeignKey(otherTableName, otherColumnName, thisColumnName) =>
          Some(
            s"ALTER TABLE ${tableName.withQuotes} ADD FOREIGN KEY (${thisColumnName.withQuotes}) REFERENCES ${otherTableName.withQuotes}(${otherColumnName.withQuotes});"
          )
      }
    case _: AlterManyFieldTypes => None
  }
}
object SQLMigrationStep {
  final case class CreateTable(
      name: String,
      columns: Vector[ColumnDefinition] = Vector.empty
  ) extends SQLMigrationStep

  case class AlterTable(tableName: String, action: AlterTableAction)
      extends SQLMigrationStep
  case class RenameTable(name: String, newName: String) extends SQLMigrationStep
  case class DropTable(name: String) extends SQLMigrationStep
  case class AlterManyFieldTypes(
      prevModel: PModel,
      changes: Vector[ChangeFieldType]
  ) extends SQLMigrationStep
}

sealed trait AlterTableAction
object AlterTableAction {
  case class AddColumn(definition: ColumnDefinition) extends AlterTableAction
  case class DropColumn(name: String, ifExists: Boolean = true)
      extends AlterTableAction
  case class RenameColumn(name: String, newName: String)
      extends AlterTableAction
  case class AddForeignKey(
      otherTableName: String,
      otherColumnName: String,
      thisColumnName: String
  ) extends AlterTableAction
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
        s" REFERENCES ${fk.otherTableName.withQuotes}(${fk.otherColumnName.withQuotes}) ${onDeleteCascade}"
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
