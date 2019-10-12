package setup

import domain.SyntaxTree

import sangria.schema.{Schema}
import sangria.ast.{Document}
import sangria.renderer.{SchemaRenderer, SchemaFilter}

import scala.util.{Try, Success, Failure}
import sys.process._
import scala.language.postfixOps


trait Migrator extends WithSyntaxTree {
  type A

  val syntaxTreeOption: Option[SyntaxTree]
  override val syntaxTree: SyntaxTree = syntaxTreeOption.get

  def run(): Try[A]
  def syntaxTree(s: SyntaxTree): Migrator
  
}

case class PrismaMigrator(
    override val syntaxTreeOption: Option[SyntaxTree] = None,
    outputHandler: String => Unit = output => println(output),
    prismaServerUri: String
) extends Migrator {
  override type A = Unit
  val converter = GraphQlConverter(syntaxTreeOption.get)


  def renderedSchema =
    syntaxTreeOption
      .map { s =>
        SchemaRenderer.renderSchema(
          Schema.buildFromAst(converter.buildGraphQLAst()),
          SchemaFilter(
            typeName =>
              typeName != "Query"
                && typeName != "Mutation"
                && typeName != "Subscription"
                && !Schema.isBuiltInType(typeName),
            dirName => !Schema.isBuiltInDirective(dirName)
          )
        )
      }
      .getOrElse("")

  override def syntaxTree(s: SyntaxTree) =
    PrismaMigrator(Some(s), outputHandler, prismaServerUri)

  override def run = Try {
    // Send data model (renderedSchema) to Prisma server
  }
}

case class MockSuccessMigrator(
    override val syntaxTreeOption: Option[SyntaxTree] = None,
) extends Migrator {
  override type A = String
  val converter = GraphQlConverter(syntaxTreeOption.get)


  def renderedSchema = ""

  override def syntaxTree(s: SyntaxTree) =
    MockSuccessMigrator(Some(s))

  override def run = Success("Mock Migration Succeeded")
}

case class MockFailureMigrator(
    override val syntaxTreeOption: Option[SyntaxTree] = None,
) extends Migrator {
  override type A = Unit
  val converter = GraphQlConverter(syntaxTreeOption.get)


  def renderedSchema = ""

  override def syntaxTree(s: SyntaxTree) =
    MockFailureMigrator(Some(s))

  override def run = Failure(new Exception("Mock Migration Failed"))
}
