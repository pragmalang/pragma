package setup
import domain.SyntaxTree
import sangria.ast.Document

case class QueryExecutor(
    schema: SyntaxTree,
    storage: Storage
) {
    def execute(query: Document) = ???
}
