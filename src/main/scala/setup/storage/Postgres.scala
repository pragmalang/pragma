package setup.storage

import setup._

import domain.SyntaxTree
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try
import domain.PModel
import running.pipeline.Operation
import sangria.ast.Document
import spray.json.JsObject
import org.jooq.impl._
import java.sql._
import org.jooq.{SQLDialect, DataType}
import domain.PModelField
// import org.jooq.CreateTableColumnStep
// import scala.language.implicitConversions
// import org.jooq.Constraint
import domain._
// import org.jooq.util.xml.jaxb.TableConstraint
// import org.jooq.util.postgres.PostgresDataType
// import org.jooq.util.postgres.PostgresDSL
// import org.jooq.util.postgres.PostgresUtils

case class Postgres(syntaxTree: SyntaxTree) extends Storage {

  val conn = DriverManager.getConnection(???, ???, ???)
  val db = DSL.using(conn, SQLDialect.POSTGRES);
  override def migrate(
      migrationSteps: Vector[MigrationStep]
  ): Future[Vector[Try[Unit]]] =
    Future.sequence(migrationSteps.map[Future[Try[Unit]]] {
      case CreateModel(model)                            => ???
      case RenameModel(modelId, newId)                   => ???
      case DeleteModel(model)                            => ???
      case UndeleteModel(model)                          => ???
      case AddField(field, model)                        => ???
      case ChangeFieldType(field, model, _, transformer) => ???
      case DeleteField(field, model)                     => ???
      case UndeleteField(field, model)                   => ???
      case RenameField(fieldId, newId, model) => {
        // val r = PostgresDSL
        ???
      }
    })

  // def createCollection(model: PModel) = {
  //   val table = db.createTable(model.id)
  //   def loop(
  //       head: PModelField,
  //       tail: Vector[PModelField],
  //       acc: CreateTableColumnStep
  //   ): CreateTableColumnStep = ???
  //   ???
  // }

  def fieldType(
      model: PModel,
      field: PModelField,
      ptype: PType,
      isOptional: Boolean = false
  ): (DataType[_], Vector[Constraint.ColumnConstraint]) = ptype match {
    case PString => (SQLDataType.VARCHAR, Vector.empty)
    case PInt    => (SQLDataType.INTEGER, Vector.empty)
    case PFloat  => (SQLDataType.FLOAT, Vector.empty)
    case PBool   => (SQLDataType.BOOLEAN, Vector.empty)
    case PDate   => (SQLDataType.DATE, Vector.empty)
    case PFile(sizeInBytes, extensions) =>
      (SQLDataType.VARCHAR(1000), Vector.empty)
    case PFunction(args, returnType)              => ???
    case POption(ptype)                           => ???
    case PInterface(id, fields, position)         => ???
    case PEnum(id, values, position)              => ???
    case PModel(id, fields, directives, position) => ???
    case PReference(id)                           => ???
    case PAny                                     => ???
    case PArray(ptype) => {
      // val tableName = arrayFieldTableName(model, field)
      // val _type = fieldType(model, field, ptype)
      val constraint = ???
      (constraint, Vector.empty)
    }
  }

  def arrayFieldTableName(model: PModel, field: PModelField): String =
    s"${model.id}_${field.id}_array"

  def parserArrayFieldTableName(name: String): Option[(String, String)] =
    name.split("_").toList match {
      case modelName :: fieldName :: tail => Some(modelName -> fieldName)
      case _                              => None
    }
  override def modelEmpty(model: PModel): Future[Boolean] = ???
  override def modelExists(model: PModel): Future[Boolean] = ???
  override def run(
      query: Document,
      operations: Map[Option[String], Vector[Operation]]
  ): Future[Try[Either[JsObject, Vector[JsObject]]]] = ???
}

sealed trait SQLMigrationStep
object SQLMigrationStep {
  final case class CreateTable(
      name: String,
      columns: Vector[ColumnDefinition[_]],
      constraints: Vector[Constraint.TableConstraint]
  ) extends SQLMigrationStep

  case class AlterTable(tableName: String, action: AlterTableAction)
      extends SQLMigrationStep
  case class RenameTable(name: String, newName: String) extends SQLMigrationStep
  case class DropTable(name: String) extends SQLMigrationStep
}

case class ColumnDefinition[T](
    name: String,
    dataType: DataType[T],
    // case class ForeignKey(column: PModelField)
    constraints: Vector[Constraint.ColumnConstraint]
)

sealed trait AlterTableAction
object AlterTableAction {
  case class AddColumn[T](definition: ColumnDefinition[T])
      extends AlterTableAction
  case class DropColumn(name: String, ifExists: Boolean = true)
      extends AlterTableAction
  case class ChangeColumnType[T](name: String, dataType: DataType[T])
      extends AlterTableAction
  case class RenameColumn(name: String, newName: String)
      extends AlterTableAction
  case class AddForeignKey(
      tableName: String,
      column: PModelField,
      thisColumnName: String
  ) extends AlterTableAction
}

sealed trait Constraint
object Constraint {

  sealed trait TableConstraint extends Constraint
  object TableConstraint {
    case class PrimaryKey(columns: Vector[PModelField]) extends TableConstraint
  }

  sealed trait ColumnConstraint extends Constraint
  object ColumnConstraint {
    case object NotNull extends ColumnConstraint
    case object Unique extends ColumnConstraint
    case object PrimaryKey extends ColumnConstraint
  }
}
