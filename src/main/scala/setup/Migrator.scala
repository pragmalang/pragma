package setup

import scala.util.{Try, Success, Failure}
import sangria.schema.{Schema}
import sangria.renderer.{SchemaRenderer, SchemaFilter}

trait Migrator {
  val schemaOption: Option[Schema[Any, Any]]
  def run(): Try[Unit]
  def schema(s: Schema[Any, Any]): Migrator
  def renderedSchema(): String
}

case class PrismaMigrator(
    schemaOption: Option[Schema[Any, Any]] = None,
    outputHandler: String => Unit = output => println(output)
) extends Migrator {
  import sys.process._
  import scala.language.postfixOps

  def renderedSchema =
    schemaOption
      .map(schemaRenderer)
      .getOrElse("")

  def schemaRenderer(schema: Schema[Any, Any]) =
    SchemaRenderer.renderSchema(
      schema,
      SchemaFilter(
        typeName =>
          typeName != "Query" && typeName != "Mutation" && typeName != "Subscription" && !Schema
            .isBuiltInType(typeName),
        dirName => !Schema.isBuiltInDirective(dirName)
      )
    )

  def schema(s: Schema[Any, Any]) = PrismaMigrator(Some(s), outputHandler)

  def run = Try {
    // val exitCode = "docker-compose up -d" ! ProcessLogger(outputHandler(_))
    // exitCode match {
    //   case 1 => throw new Exception("Prisma migration failed")
    // }
  }
}

case class MockSuccessMigrator(schemaOption: Option[Schema[Any, Any]])
    extends Migrator {
  def run = Success(())
  def schema(s: Schema[Any, Any]) = MockSuccessMigrator(schemaOption)
  def renderedSchema: String = ""
}

case class MockFailureMigrator(schemaOption: Option[Schema[Any, Any]])
    extends Migrator {
  def run = Failure(new Exception("Mock Migrator failed"))
  def schema(s: Schema[Any, Any]) = MockFailureMigrator(schemaOption)
  def renderedSchema: String = ""
}
