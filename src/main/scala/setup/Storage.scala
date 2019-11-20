package setup
import sangria.ast.Document
import domain.SyntaxTree
import running.Request

trait Storage {
  val syntaxTree: SyntaxTree
  val migrator: Migrator

  def runQuery(query: Document): Request
  def migrate() = migrator.run()
  def dockerComposeYaml(): DockerCompose
}

case class PrismaMongo(syntaxTree: SyntaxTree) extends Storage {
  val converter: Converter = GraphQlConverter(syntaxTree)
  override val migrator: Migrator = PrismaMongoMigrator(syntaxTree)
  override def runQuery(query: Document): Request = ???
  override def dockerComposeYaml() = ???
}
