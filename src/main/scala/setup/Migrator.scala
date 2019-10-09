package setup

import scala.util.{Try}
import sangria.schema.{Schema}
import sangria.renderer.{SchemaRenderer, SchemaFilter}

trait Migrator {
  def apply(schema: Schema[Any, Any]): Try[Unit]
}

case object PrismaMigrator extends Migrator {
  def apply(schema: Schema[Any, Any]) =
    ??? // TODO: use SchemaFilter when rendering the schema
}
