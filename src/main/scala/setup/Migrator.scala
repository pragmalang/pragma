package setup

import scala.util.{Try, Success, Failure}
import sangria.schema.{Schema}
import sangria.ast.{Document}
import sangria.renderer.{SchemaRenderer, SchemaFilter}

trait Migrator {
  val schemaOption: Option[Document]
  def run(): Try[Unit]
  def schema(s: Document): Migrator
  def renderedSchema(): String
}

case class PrismaMigrator(
    schemaOption: Option[Document] = None,
    outputHandler: String => Unit = output => println(output)
) extends Migrator {
  import sys.process._
  import scala.language.postfixOps

  def renderedSchema =
    schemaOption
      .map(schemaRenderer)
      .getOrElse("")

  def schemaRenderer(schema: Document) =
    SchemaRenderer.renderSchema(
      Schema.buildFromAst(schema),
      SchemaFilter(
        typeName =>
          typeName != "Query" && typeName != "Mutation" && typeName != "Subscription" && !Schema
            .isBuiltInType(typeName),
        dirName => !Schema.isBuiltInDirective(dirName)
      )
    )

  def schema(s: Document) = PrismaMigrator(Some(s), outputHandler)

  def run = Try {
    // val exitCode = "docker-compose up -d" ! ProcessLogger(outputHandler(_))
    // exitCode match {
    //   case 1 => throw new Exception("Prisma migration failed")
    // }
  }
}

case class MockSuccessMigrator(schemaOption: Option[Document])
    extends Migrator {
  def run = Success(())
  def schema(s: Document) = MockSuccessMigrator(schemaOption)
  def renderedSchema: String = ""
}

case class MockFailureMigrator(schemaOption: Option[Document])
    extends Migrator {
  def run = Failure(new Exception("Mock Migrator failed"))
  def schema(s: Document) = MockFailureMigrator(schemaOption)
  def renderedSchema: String = ""
}
