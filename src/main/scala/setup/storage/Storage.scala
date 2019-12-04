package setup.storage
import sangria.ast.Document
import domain.SyntaxTree
import running.Request
import scala.util.Try
import setup._, setup.utils._

trait Storage {
  val syntaxTree: SyntaxTree
  val migrator: Migrator

  def runQuery(query: Document): Request
  def validateQuery(query: Document): Try[Request]
  def migrate() = migrator.run()
  def dockerComposeYaml(): DockerCompose
}

case class PrismaMongo(syntaxTree: SyntaxTree) extends Storage {
  override val migrator: Migrator = PrismaMongoMigrator(syntaxTree)
  override def runQuery(query: Document): Request = ???
  override def validateQuery(query: Document): Try[Request] = ???
  override def dockerComposeYaml() = ???
}
