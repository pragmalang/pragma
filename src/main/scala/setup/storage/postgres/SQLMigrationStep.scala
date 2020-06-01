package setup.storage.postgres

import SQLMigrationStep._
import org.jooq._
import setup.storage.postgres.AlterTableAction._
import domain.PModelField

sealed trait SQLMigrationStep {
  def renderSQL: String = this match {
    case CreateTable(name, columns) => {
      val prefix = s"""CREATE TABLE IF NOT EXISTS "${name}""""
      val cols = "(\n" + columns.map(_.render).mkString(",\n") + ");\n"
      prefix + cols
    }
    case RenameTable(name, newName) => s"""
    ALTER TABLE "${name}"
      RENAME TO "${newName}";
    """
    case DropTable(name)            => s"""
    DROP TABLE IF EXISTS "${name}" CASCADE;
    """
    case AlterTable(tableName, action) =>
      action match {
        case AddColumn(definition) =>
          s"""
        ALTER TABLE "${tableName}" ADD COLUMN ${definition.render};
        """
        case DropColumn(name, ifExists) =>
          s"""
          ALTER TABLE "${tableName}" DROP COLUMN ${if (ifExists) "IF EXISTS"
          else ""} ${name} CASCADE;
        """
        case ChangeColumnType(name, dataType) =>
          s"""
        ALTER TABLE "${tableName}" ALTER COLUMN ${name} TYPE ${dataType
            .getTypeName()};
        """
        case RenameColumn(name, newName) =>
          s"""
        ALTER TABLE "${tableName}" 
        RENAME COLUMN ${name} TO ${newName};
        """
        case AddForeignKey(otherTableName, otherColumnName, thisColumnName) =>
          s"""
        ALTER TABLE "${tableName}"
          ADD FOREIGN KEY ${thisColumnName} REFERENCES "${otherTableName}"(${otherColumnName}) ON DELETE CASCADE;
        """
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
    val colPrefix = s"${name} ${dataType.getTypeName()}"
    val notNull = if (isNotNull) "NOT NULL" else ""
    val unique = if (isUnique) "UNIQUE" else ""
    val uuid = if (isUUID) "DEFAULT uuid_generate_v4 ()" else ""
    val primaryKey = if (isPrimaryKey) "PRIMARY KEY" else ""
    val autoIncrement = ""
    val fk = foreignKey match {
      case Some(fk) =>
        s"""REFERENCES "${fk.otherTableName}"(${fk.otherColumnName}) ON DELETE CASCADE"""
      case None => ""
    }

    colPrefix + notNull + unique + uuid + primaryKey + autoIncrement + fk
  }
}

case class ForeignKey(
    otherTableName: String,
    otherColumnName: String
)

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

sealed trait Constraint
object Constraint {
  sealed trait TableConstraint extends Constraint
  object TableConstraint {
    case class PrimaryKey(columns: Vector[PModelField]) extends TableConstraint
  }
}
