package setup

import scala.util.{Try, Success, Failure}
import sangria.schema.{Schema}
import sangria.renderer.{SchemaRenderer, SchemaFilter}

trait Migrator {
  def apply(schema: Schema[Any, Any]): Try[String]
}

case object PrismaMigrator extends Migrator {
  import sys.process._
  import scala.language.postfixOps

  def apply(schema: Schema[Any, Any]) =
    apply(schema, (output: String) => println(output))

  def apply(schema: Schema[Any, Any], outputHandler: String => Unit) = Try {
    val renderedSchema = SchemaRenderer.renderSchema(
      schema,
      SchemaFilter(
        typeName => typeName != "Query" && typeName != "Mutation" && typeName != "Subscription" && !Schema.isBuiltInType(typeName),
        dirName => !Schema.isBuiltInDirective(dirName)
      )
    );
    renderedSchema
    // val exitCode = "docker-compose up -d" ! ProcessLogger(println(_))
  }
}

case object MockSuccessMigrator extends Migrator {
  def apply(schema: Schema[Any, Any]) = Success("Mock Migrator Succeeded")
}

case object MockFailureMigrator extends Migrator {
  def apply(schema: Schema[Any, Any]) =
    Failure(new Exception("Mock Migrator failed"))
}
