package setup.storage.postgres

import setup._, storage._
import domain.SyntaxTree
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import domain.PModel
import domain.PModelField
import cats.implicits._

class Postgres(
    syntaxTree: SyntaxTree,
    migrationEngine: MigrationEngine[Postgres, Future],
    queryEngine: QueryEngine[Postgres, Future]
) extends Storage[Postgres, Future](queryEngine, migrationEngine) {

  def arrayFieldTableName(model: PModel, field: PModelField): String =
    s"${model.id}_${field.id}_array"

  def parserArrayFieldTableName(name: String): Option[(String, String)] =
    name.split("_").toList match {
      case modelName :: fieldName :: tail => Some(modelName -> fieldName)
      case _                              => None
    }
}
