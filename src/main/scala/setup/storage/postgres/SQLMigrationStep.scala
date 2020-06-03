package setup.storage.postgres

import SQLMigrationStep._
import org.jooq._
import setup.storage.postgres.AlterTableAction._
import domain.Implicits._

sealed trait SQLMigrationStep {
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
        case ChangeColumnType(name, dataType) =>
          s"ALTER TABLE ${tableName.withQuotes} ALTER COLUMN ${name.withQuotes} TYPE ${dataType.getTypeName()};"
        case RenameColumn(name, newName) =>
          s"ALTER TABLE ${tableName.withQuotes} RENAME COLUMN ${name.withQuotes} TO ${newName.withQuotes};"
        case AddForeignKey(otherTableName, otherColumnName, thisColumnName) =>
          s"ALTER TABLE ${tableName.withQuotes} ADD FOREIGN KEY (${thisColumnName.withQuotes}) REFERENCES ${otherTableName.withQuotes}(${otherColumnName.withQuotes});"
      }
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
}

sealed trait AlterTableAction
object AlterTableAction {
  case class AddColumn(definition: ColumnDefinition) extends AlterTableAction
  case class DropColumn(name: String, ifExists: Boolean = true)
      extends AlterTableAction
  case class ChangeColumnType(name: String, dataType: DataType[_])
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
    dataType: DataType[_],
    isNotNull: Boolean,
    isUnique: Boolean,
    isPrimaryKey: Boolean,
    isAutoIncrement: Boolean,
    isUUID: Boolean,
    foreignKey: Option[ForeignKey]
) {
  def render = {
    val colPrefix = s"${name.withQuotes} ${dataType.getTypeName()}"
    val notNull = if (isNotNull) " NOT NULL" else ""
    val unique = if (isUnique) " UNIQUE" else ""
    val uuid = if (isUUID) " DEFAULT uuid_generate_v4 ()" else ""
    val primaryKey = if (isPrimaryKey) " PRIMARY KEY" else ""
    val autoIncrement = ""
    val fk = foreignKey match {
      case Some(fk) =>
        s" REFERENCES ${fk.otherTableName.withQuotes}(${fk.otherColumnName.withQuotes})"
      case None => ""
    }

    colPrefix + notNull + unique + uuid + primaryKey + autoIncrement + fk
  }
}

case class ForeignKey(
    otherTableName: String,
    otherColumnName: String
)
