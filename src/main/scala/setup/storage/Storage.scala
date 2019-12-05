package setup.storage
import sangria.ast.Document
import domain.SyntaxTree
import scala.util.Try
import setup._, setup.utils._
import spray.json.JsValue

trait Storage {
  val syntaxTree: SyntaxTree
  val migrator: Migrator

  def runQuery(query: Document): JsValue
  def validateQuery(query: Document): Try[JsValue]
  def reduceQuery(query: Document): Document
  def migrate() = migrator.run()
  def dockerComposeYaml(): DockerCompose
}

case class PrismaMongo(syntaxTree: SyntaxTree) extends Storage {
  override val migrator: Migrator = PrismaMongoMigrator(syntaxTree)
  override def runQuery(query: Document): JsValue = ???
  override def validateQuery(query: Document): Try[JsValue] = ???
  override def dockerComposeYaml() = ???
  override def reduceQuery(query: Document): Document = ???
}
