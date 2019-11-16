package setup

import domain.SyntaxTree

import sangria.schema.{Schema}
import sangria.ast.{Document}
import sangria.renderer.{SchemaRenderer, SchemaFilter}

import scala.util.{Try, Success, Failure}
import sys.process._
import scala.language.postfixOps

trait Migrator {
  type Return
  val syntaxTree: SyntaxTree
  def run(): Try[Return]
}

case class PrismaMongoMigrator(
    override val syntaxTree: SyntaxTree,
    outputHandler: String => Unit = output => println(output),
    prismaServerUri: String = "http://localhost:4466"
) extends Migrator {
  override type Return = Unit
  val converter = GraphQlConverter(syntaxTree)

  def renderSchema(schema: Document) =
    SchemaRenderer.renderSchema(
      Schema.buildFromAst(schema),
      SchemaFilter(
        typeName =>
          typeName != "Query"
            && typeName != "Mutation"
            && typeName != "Subscription"
            && !Schema.isBuiltInType(typeName),
        dirName => !Schema.isBuiltInDirective(dirName)
      )
    )

  override def run = Try {
    val graphQlDefinitions = converter.buildGraphQLAst()
    val prismaSchema = toValidPrismaSchema(graphQlDefinitions)
    val renderedPrismaSchema = renderSchema(prismaSchema)
    // ??? TODO: Send renderedPrismaSchema to Prisma server
  }

  def toValidPrismaSchema(schema: Document): Document = ???
}

case class MockSuccessMigrator(
    override val syntaxTree: SyntaxTree
) extends Migrator {
  override type Return = String
  val converter = GraphQlConverter(syntaxTree)

  def renderedSchema = ""

  override def run = Success("Mock Migration Succeeded")
}

case class MockFailureMigrator(
    override val syntaxTree: SyntaxTree
) extends Migrator {
  override type Return = Unit
  val converter = GraphQlConverter(syntaxTree)

  def renderedSchema = ""

  override def run = Failure(new Exception("Mock Migration Failed"))
}
