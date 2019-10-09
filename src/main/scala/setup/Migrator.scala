package setup

import scala.util.{Try, Success, Failure}
import sangria.schema.{Schema}
import sangria.renderer.{SchemaRenderer, SchemaFilter}

trait Migrator {
  def apply(schema: Schema[Any, Any]): Try[Unit]
}

case object PrismaMigrator extends Migrator {
  def apply(schema: Schema[Any, Any]) =
    ??? // TODO: use SchemaFilter when rendering the schema
}

case object MockSuccessMigrator extends Migrator {
  def apply(schema: Schema[Any,Any]): Try[Unit] = Success(())
}

case object MockFailureMigrator extends Migrator {
  def apply(schema: Schema[Any,Any]): Try[Unit] = Failure(new Exception("Mock Migrator failed"))
}
