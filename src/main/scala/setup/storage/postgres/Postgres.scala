package setup.storage.postgres

import setup._, storage._
import domain.SyntaxTree
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import domain.PModel
import org.jooq.impl._
import java.sql._
import org.jooq._
import domain.PModelField
import domain._
import cats.implicits._

class Postgres(
    syntaxTree: SyntaxTree,
    migrationEngine: MigrationEngine[Postgres, Future],
    queryEngine: QueryEngine[Postgres, Future]
) extends Storage[Postgres, Future](queryEngine, migrationEngine) {

  val conn = DriverManager.getConnection(???, ???, ???)
  val db = DSL.using(conn, SQLDialect.POSTGRES);

  def arrayFieldTableName(model: PModel, field: PModelField): String =
    s"${model.id}_${field.id}_array"

  def parserArrayFieldTableName(name: String): Option[(String, String)] =
    name.split("_").toList match {
      case modelName :: fieldName :: tail => Some(modelName -> fieldName)
      case _                              => None
    }
}