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

case class Relationship(
    from: (PModel, PModelField),
    to: (PModel, Option[PModelField]),
    name: Option[String],
    kind: RelationshipKind
)

sealed trait RelationshipKind
object RelationshipKind {
  final case object ManyToMany extends RelationshipKind
  final case object OneToMany extends RelationshipKind
  final case object OneToOne extends RelationshipKind
}
object Relationship {

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
  ): RelationshipKind = (from, to) match {
    case (PArray(_) | POption(PArray(_)), PArray(_) | POption(PArray(_))) =>
      RelationshipKind.ManyToMany
    case (PArray(_) | POption(PArray(_)), _) => RelationshipKind.OneToMany
    case (_, PArray(_) | POption(PArray(_))) => RelationshipKind.OneToMany
    case (_, _)                              => RelationshipKind.OneToOne
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

  private def innerModel(ptype: PType, syntaxTree: SyntaxTree): Option[PModel] =
    ptype match {
      case PArray(t)      => innerModel(t, syntaxTree)
      case POption(t)     => innerModel(t, syntaxTree)
      case PReference(id) => Some(syntaxTree.models.find(_.id == id).get)
      case model: PModel  => Some(model)
      case _              => None
    }

  private def relationship(
      relName: Option[String],
      ptype: PType,
      field: PModelField,
      model: PModel,
      syntaxTree: SyntaxTree
  ): Relationship = {
    val otherFieldPathOption =
      relName.flatMap(connectedFieldPath(_)(syntaxTree))

    (otherFieldPathOption, relName) match {
      case (Some((otherModel, otherField)), Some(relName)) =>
        Relationship(
          (otherModel, otherField),
          (model, Some(field)),
          Some(relName),
          relationshipKind(ptype, otherField.ptype)
        ) // either `OneToOne` or `ManyToMany`.
      case _ =>
        Relationship(
          (model, field),
          (innerModel(ptype, syntaxTree).get, None),
          None,
          relationshipKind(model, ptype)
        ) // either `OneToOne` or `OneToMany`.
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
        (relationshipName, field, field.ptype)
      })
      .filter(pair => notPrimitiveNorEnum(pair._3))
      .map(pair => relationship(pair._1, pair._3, pair._2, model, syntaxTree))
      .toVector

  def from(syntaxTree: SyntaxTree): Vector[Relationship] =
    syntaxTree.models
      .flatMap(relationships(_, syntaxTree))
      .distinctBy(_.kind)
      .toVector
      // .foldLeft(Vector.empty[Relationship]) { // Only non-equivalent `Relationship`s
      //   case (acc, rel) if !acc.contains(rel) => acc :+ rel
      //   case (acc, _)                         => acc
      // }
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
