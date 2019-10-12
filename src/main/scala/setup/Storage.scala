package setup
import sangria.ast.Document
import domain.SyntaxTree

trait Storage extends WithSyntaxTree {
  type Request // TODO: remove this into a seperate type

  override val syntaxTree: SyntaxTree
  val migrator: Migrator

  def runQuery(query: Document): Request
  def migrate() = migrator.syntaxTree(syntaxTree).run()
}

case class PrismaMongo[Request](syntaxTree: SyntaxTree) extends Storage {
  def runQuery(query: Document): Request = ???
  val converter: Converter = GraphQlConverter(syntaxTree)
  val migrator: Migrator = ???
}
