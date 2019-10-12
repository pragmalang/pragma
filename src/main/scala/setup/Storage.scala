package setup
import sangria.ast.Document
import domain.SyntaxTree

trait Storage extends WithSyntaxTree {
  type Request // TODO: remove this into a seperate type

  override val syntaxTree: SyntaxTree
  val migrator: Migrator

  def runQuery(query: Document): Request
  def migrate() = migrator.syntaxTree(syntaxTree).run()
  // TODO: add `dockerContainerConfig` method that returns a DockerConfig object to be used in Setup.dockerComposeYaml
}

case class PrismaMongo[Request](syntaxTree: SyntaxTree) extends Storage {
  val converter: Converter = GraphQlConverter(syntaxTree)
  val migrator: Migrator = PrismaMongoMigrator(Some(syntaxTree))
  def runQuery(query: Document): Request = ???
}
