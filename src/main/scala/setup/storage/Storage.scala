package setup.storage

import sangria.ast.Document
import domain.SyntaxTree
import scala.util.Try
import setup.utils._
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

case class MockStorage(syntaxTree: SyntaxTree) extends Storage {
  override def dockerComposeYaml(): DockerCompose = ???
  override def reduceQuery(query: Document): Document = ???
  override def migrate(): Try[migrator.Return] = ???
  override val migrator: Migrator = MockSuccessMigrator(syntaxTree)
  override def runQuery(query: Document): JsValue = ???
  override def validateQuery(query: Document): Try[JsValue] = ???
}
