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
import domain.primitives._
// import scala.language.implicitConversions
// import org.jooq.Constraint
import domain.PType
// import org.jooq.util.xml.jaxb.TableConstraint
// import org.jooq.util.postgres.PostgresDataType
// import org.jooq.util.postgres.PostgresDSL
// import org.jooq.util.postgres.PostgresUtils
import domain.PReference
import domain.utils._
import domain.PEnum

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
    case PArray(ptype) => {
      // val tableName = arrayFieldTableName(model, field)
      // val _type = fieldType(model, field, ptype)
      val constraint = ???
      (constraint, Vector.empty)
    }
    case PFunction(args, returnType) => ???
    case POption(ptype)              => ???
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

trait Relationship {
  val from: PModel
  val to: PModel
  val name: Option[String]

  override def equals(that: Any): Boolean = (this, that) match {
    case (_: Relationship.OneToOne, that: Relationship.OneToOne) =>
      (this.from.id == that.from.id && this.to.id == that.to.id && this.name == that.name) ||
        (this.from.id == that.to.id && this.to.id == that.from.id && this.name == that.name)
    case (_: Relationship.OneToMany, that: Relationship.OneToMany) =>
      this.from.id == that.from.id && this.to.id == that.to.id && this.name == that.name
    case (_: Relationship.ManyToMany, that: Relationship.ManyToMany) =>
      (this.from.id == that.from.id && this.to.id == that.to.id && this.name == that.name) ||
        (this.from.id == that.to.id && this.to.id == that.from.id && this.name == that.name)
    case _ => false
  }
}

object Relationship {
  final case class ManyToMany(from: PModel, to: PModel, name: Option[String])
      extends Relationship
  final case class OneToMany(from: PModel, to: PModel, name: Option[String])
      extends Relationship
  final case class OneToOne(from: PModel, to: PModel, name: Option[String])
      extends Relationship

  private def notPrimitiveNorEnum(t: PType): Boolean = t match {
    case PArray(ptype)  => notPrimitiveNorEnum(t)
    case POption(ptype) => notPrimitiveNorEnum(t)
    case _: PModel      => true
    case _: PReference  => true
    case _: PEnum       => false
  }

  private def relationshipKind(
      from: PType,
      to: PType
  ): (PModel, PModel, Option[String]) => Relationship = (from, to) match {
    case (PArray(_) | POption(PArray(_)), PArray(_) | POption(PArray(_))) =>
      ManyToMany.apply
    case (PArray(_) | POption(PArray(_)), _) => OneToMany.apply
    case (_, PArray(_) | POption(PArray(_))) => OneToMany.apply
    case (_, _)                              => OneToOne.apply
  }

  private def connectedFieldPath(
      relName: String
  )(syntaxTree: SyntaxTree): Option[(PModel, PModelField)] = {
    val modelOption = syntaxTree.models.find(
      model =>
        model.fields.exists(_.directives.find(_.id == "connection").isDefined)
    )

    val fieldOption = modelOption.flatMap(
      model =>
        model.fields.find(_.directives.find(_.id == "connection").isDefined)
    )

    (modelOption, fieldOption) match {
      case (Some(model), Some(field)) => Some(model -> field)
      case _                          => None
    }
  }

  private def relationship(
      relName: Option[String],
      ptype: PType
  )(model: PModel, syntaxTree: SyntaxTree): Relationship = {
    val otherFieldPathOption =
      relName.flatMap(connectedFieldPath(_)(syntaxTree))

    lazy val innerType: PType => PModel = ptype =>
      ptype match {
        case PArray(t)      => innerType(t)
        case POption(t)     => innerType(t)
        case PReference(id) => syntaxTree.models.find(_.id == id).get
        case model: PModel  => model
      }

    (otherFieldPathOption, relName) match {
      case (Some((otherModel, otherField)), Some(relName)) =>
        relationshipKind(ptype, otherField.ptype)(
          model,
          otherModel,
          Some(relName)
        )
      case _ =>
        // either `OneToOne` or `OneToMany`.
        relationshipKind(model, ptype)(model, innerType(ptype), None)
    }
  }

  private def relationships(
      model: PModel,
      syntaxTree: SyntaxTree
  ): Vector[Relationship] =
    model.fields
      .map(field => {
        val relationshipName = field.directives
          .find(_.id == "connection")
          .flatMap(
            _.args
              .value("connection") match {
              case PStringValue(value) => Some(value)
              case _                   => None
            }
          )
        relationshipName -> field.ptype
      })
      .filter(pair => notPrimitiveNorEnum(pair._2))
      .map(pair => relationship(pair._1, pair._2)(model, syntaxTree))
      .toVector

  def from(syntaxTree: SyntaxTree): Vector[Relationship] =
    syntaxTree.models
      .flatMap(relationships(_, syntaxTree))
      .foldLeft(Vector.empty[Relationship]) { // Only non-equivalent `Relationship`s
        case (acc, rel) if !acc.contains(rel) => acc :+ rel
        case (acc, _)                         => acc
      }
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
    // case class ForeignKey(tableName: String, column: PModelField)
    //     extends ColumnConstraint
  }
}

/*
# Invalid examples on using `@connection`

model A {
  f1: String
  f2: Int
  b: [B] @connection("Bs")
}

model B {
  ...
}

model C {
  b: [B] @connection("Bs")
}

# or

model C {
  a: [A] @connection("Bs")
}


# if `@connection` is used on a field `b` of type `B` on model `A`,
# then there must be a field of type `A` on model `B` that has the `@connection` name.

 */
