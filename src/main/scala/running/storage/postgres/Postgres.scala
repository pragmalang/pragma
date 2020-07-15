package running.storage.postgres

import running.storage._
import domain.SyntaxTree
import domain.PModel
import domain.PModelField
import cats.implicits._
import cats._

class Postgres[M[_]: Monad](
    syntaxTree: SyntaxTree,
    migrationEngine: PostgresMigrationEngine[M],
    queryEngine: PostgresQueryEngine[M]
) extends Storage[Postgres[M], M](queryEngine, migrationEngine) {

  def arrayFieldTableName(model: PModel, field: PModelField): String =
    s"${model.id}_${field.id}_array"

  def parserArrayFieldTableName(name: String): Option[(String, String)] =
    name.split("_").toList match {
      case modelName :: fieldName :: tail => Some(modelName -> fieldName)
      case _                              => None
    }
}
