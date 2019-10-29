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

  val syntaxTreeOption: Option[SyntaxTree]

  def run(): Try[Return]
  def syntaxTree(s: SyntaxTree): Migrator
}

case class PrismaMongoMigrator(
    override val syntaxTreeOption: Option[SyntaxTree] = None,
    outputHandler: String => Unit = output => println(output),
    prismaServerUri: String = "http://localhost:4466"
) extends Migrator {
  override type Return = Unit
  val converter = GraphQlConverter(syntaxTreeOption.get)

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

  override def syntaxTree(s: SyntaxTree) =
    PrismaMongoMigrator(Some(s), outputHandler, prismaServerUri)

  override def run = Try {
    val graphQlDefinitions = converter.buildGraphQLAst()
    val prismaSchema = toValidPrismaSchema(graphQlDefinitions)
    val renderedPrismaSchema = renderSchema(prismaSchema)
    // ??? TODO: Send renderedPrismaSchema to Prisma server
  }

  def toValidPrismaSchema(schema: Document): Document = ???
}

case class MockSuccessMigrator(
    override val syntaxTreeOption: Option[SyntaxTree] = None
) extends Migrator {
  override type Return = String
  val converter = GraphQlConverter(syntaxTreeOption.get)

  def renderedSchema = ""

  override def syntaxTree(s: SyntaxTree) =
    MockSuccessMigrator(Some(s))

  override def run = Success("Mock Migration Succeeded")
}

case class MockFailureMigrator(
    override val syntaxTreeOption: Option[SyntaxTree] = None
) extends Migrator {
  override type Return = Unit
  val converter = GraphQlConverter(syntaxTreeOption.get)

  def renderedSchema = ""

  override def syntaxTree(s: SyntaxTree) =
    MockFailureMigrator(Some(s))

  override def run = Failure(new Exception("Mock Migration Failed"))
}
