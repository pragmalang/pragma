package setup.storage

import setup._, utils._

import domain.SyntaxTree

import sangria.schema.{Schema}
import sangria.ast.{Document}
import sangria.renderer.{SchemaRenderer, SchemaFilter}

import scala.util.{Try, Success, Failure}
import sys.process._
import scala.language.postfixOps
import sangria.ast.Definition

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

  override def run = Try {
    val renderedSchema = validSchema.renderPretty
    // ??? TODO: Send renderedPrismaSchema to Prisma server
  }

  def validSchema: Document = ???
}

case class MockSuccessMigrator(
    override val syntaxTree: SyntaxTree
) extends Migrator {
  override type Return = String
  val converter = GraphQlConverter(syntaxTree)

  override def run = Success("Mock Migration Succeeded")
}

case class MockFailureMigrator(
    override val syntaxTree: SyntaxTree
) extends Migrator {
  override type Return = Unit
  val converter = GraphQlConverter(syntaxTree)

  override def run = Failure(new Exception("Mock Migration Failed"))
}
