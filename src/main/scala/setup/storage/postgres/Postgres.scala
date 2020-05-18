package setup.storage.postgres

import util._
import setup._, storage._
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
import org.jooq._
import domain.PModelField
// import org.jooq.CreateTableColumnStep
// import scala.language.implicitConversions
// import org.jooq.Constraint
import domain._
// import org.jooq.util.xml.jaxb.TableConstraint
// import org.jooq.util.postgres.PostgresDataType
// import org.jooq.util.postgres.PostgresDSL
// import org.jooq.util.postgres.PostgresUtils
import SQLMigrationStep._
import domain.RelationKind.ManyToMany
import domain.RelationKind.OneToMany
import domain.RelationKind.OneToOne

// import scala.jdk.CollectionConverters._

import cats.implicits._
import setup.storage.postgres.AlterTableAction.AddColumn
import setup.storage.postgres.AlterTableAction.DropColumn
import setup.storage.postgres.AlterTableAction.ChangeColumnType
import setup.storage.postgres.AlterTableAction.RenameColumn
import setup.storage.postgres.AlterTableAction.AddForeignKey

case class Postgres(syntaxTree: SyntaxTree) extends Storage {

  val conn = DriverManager.getConnection(???, ???, ???)
  val db = DSL.using(conn, SQLDialect.POSTGRES);
  override def migrate(
      migrationSteps: Vector[MigrationStep]
  ): Future[Vector[Try[Unit]]] =
    Future.sequence(migrationSteps.map[Future[Try[Unit]]] {
      case CreateModel(model) => {
        ???
      }
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

sealed trait SQLMigrationStep {
  import Constraint.ColumnConstraint._
  def toJooq(implicit dsl: DSLContext): DDLQuery = this match {
    case CreateTable(name, columns) => {
      val tableWithoutConstraints =
        columns.foldLeft(dsl.createTable(s""""$name"""")) {
          case (acc, column) => acc.column(column.name, column.dataType)
        }

      val columnsWithConstraints = columns.filter {
        case ColumnDefinition(_, _, constraints) => !constraints.isEmpty
      }

      val tableWithConstraints = columnsWithConstraints
        .foldLeft[CreateTableConstraintStep](tableWithoutConstraints) {
          case (acc, column) if column.constraints.contains(NotNull) => acc
          case (acc, column) if column.constraints.contains(PrimaryKey) =>
            acc.constraint(DSL.constraint().primaryKey(column.name))
          case (acc, column) if column.constraints.contains(Unique) =>
            acc.constraint(DSL.constraint().unique(column.name))
          case (acc, column)
              if column.constraints.exists(_.isInstanceOf[ForeignKey]) => {
            val fk = column.constraints
              .find(_.isInstanceOf[ForeignKey])
              .get
              .asInstanceOf[ForeignKey]
            acc.constraint(
              DSL
                .constraint()
                .foreignKey(column.name, fk.otherColumnName)
                .references(fk.otherTableName)
            )
          }
        }
      tableWithConstraints
    }
    case RenameTable(name, newName) => dsl.alterTable(name).renameTo(newName)
    case DropTable(name)            => dsl.dropTable(name)
    case AlterTable(tableName, action) =>
      action match {
        case AddColumn(definition) => {
          val s = dsl.alterTable(tableName)
          ???
        }
        case DropColumn(name, ifExists) =>
          if (ifExists) dsl.alterTable(tableName).dropColumnIfExists(name)
          else dsl.alterTable(tableName).dropColumn(name)
        case ChangeColumnType(name, dataType) =>
          dsl.alterTable(tableName).alterColumn(name).set(dataType)
        case RenameColumn(name, newName) =>
          dsl.alterTable(tableName).renameColumn(name).to(newName)
        case AddForeignKey(otherTableName, otherColumnName, thisColumnName) =>
          dsl
            .alterTable(tableName)
            .add(
              DSL
                .constraint()
                .foreignKey(thisColumnName, otherColumnName)
                .references(otherTableName)
            )
      }
  }

  def sql(implicit dsl: DSLContext) = toJooq(dsl).getSQL()
}
object SQLMigrationStep {
  final case class CreateTable(
      name: String,
      columns: Vector[ColumnDefinition] = Vector.empty
  ) extends SQLMigrationStep

  object CreateTable {
    def from(model: PModel): CreateTable =
      CreateTable(
        model.id,
        model.fields.toVector
          .map(ColumnDefinition.fromPrimitiveField)
          .collect { case Some(field) => field }
      )
  }

  case class AlterTable(tableName: String, action: AlterTableAction)
      extends SQLMigrationStep

  object SQLMigrationStep {
    def from[K <: RelationKind](
        relations: Vector[Relation[K]]
    ): Vector[SQLMigrationStep] =
      relations.flatMap { relation =>
        relation.kind match {
          case ManyToMany =>
            Vector {
              val firstColumn = ColumnDefinition(
                relation.originTableName,
                toPostgresType(relation.origin._1.primaryField.ptype).get._1,
                Vector.empty
              )

              val secondColumn = ColumnDefinition(
                relation.targetTableName,
                toPostgresType(relation.target._1.primaryField.ptype).get._1,
                Vector.empty
              )

              CreateTable(
                relation.manyToManyTableName,
                Vector(firstColumn, secondColumn)
              )
            }
          case OneToMany =>
            Vector {
              val columnDef = ColumnDefinition(
                relation.oneToManyFkName,
                toPostgresType(relation.origin._1.primaryField.ptype).get._1,
                Vector(
                  Constraint.ColumnConstraint.ForeignKey(
                    relation.originTableName,
                    relation.origin._1.primaryField.id
                  )
                )
              )
              AlterTable(
                relation.targetTableName,
                AlterTableAction.AddColumn(columnDef)
              )
            }
          case OneToOne => {
            val fromFk = Constraint.ColumnConstraint.ForeignKey(
              relation.originTableName,
              relation.origin._1.primaryField.id
            )

            val fromColumn = ColumnDefinition(
              relation.origin._2.id,
              toPostgresType(relation.origin._1.primaryField.ptype).get._1,
              Vector(fromFk, Constraint.ColumnConstraint.Unique)
            )

            val toAlter = AlterTable(
              relation.targetTableName,
              AlterTableAction.AddColumn(fromColumn)
            )

            val toFk = Constraint.ColumnConstraint.ForeignKey(
              relation.targetTableName,
              relation.target._1.primaryField.id
            )

            val toColumn = relation.target._2.map { field =>
              ColumnDefinition(
                field.id,
                toPostgresType(relation.target._1.primaryField.ptype).get._1,
                Vector(toFk, Constraint.ColumnConstraint.Unique)
              )
            }

            val fromAlter = toColumn.map { toColumn =>
              AlterTable(
                relation.originTableName,
                AlterTableAction.AddColumn(toColumn)
              )
            }

            fromAlter match {
              case Some(fromAlter) => Vector(toAlter, fromAlter)
              case None            => Vector(toAlter)
            }
          }
        }
      }
  }
  case class RenameTable(name: String, newName: String) extends SQLMigrationStep
  case class DropTable(name: String) extends SQLMigrationStep
}

case class ColumnDefinition(
    name: String,
    dataType: DataType[_],
    constraints: Vector[Constraint.ColumnConstraint]
)

object ColumnDefinition {
  def fromPrimitiveField(field: PModelField): Option[ColumnDefinition] =
    toPostgresType(field.ptype).map { pair =>
      val withNotNull = pair._2
      val withPrimary = field.directives
        .find(_.id == "primary")
        .map(_ => Constraint.ColumnConstraint.PrimaryKey)
      val withUnique = field.directives
        .find(_.id == "unique")
        .map(_ => Constraint.ColumnConstraint.Unique)
      val constraints: Vector[Constraint.ColumnConstraint] =
        Vector(withUnique, withPrimary, withNotNull).collect {
          case Some(constraint) => constraint
        }
      ColumnDefinition(field.id, pair._1, constraints)
    }
}

sealed trait AlterTableAction
object AlterTableAction {
  case class AddColumn(definition: ColumnDefinition) extends AlterTableAction
  case class DropColumn(name: String, ifExists: Boolean = true)
      extends AlterTableAction
  case class ChangeColumnType[T](name: String, dataType: DataType[T])
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

  sealed trait ColumnConstraint extends Constraint
  object ColumnConstraint {
    case object NotNull extends ColumnConstraint
    case object Unique extends ColumnConstraint
    case object PrimaryKey extends ColumnConstraint
    case class ForeignKey(
        otherTableName: String,
        otherColumnName: String
    ) extends ColumnConstraint
  }
}
