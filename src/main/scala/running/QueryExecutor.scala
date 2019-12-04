package running
import domain.SyntaxTree
import sangria.ast.Document
import setup.storage.Storage

case class QueryExecutor(
    schema: SyntaxTree,
    storage: Storage
) {
    def execute(query: Document, userToken: JwtPaylod) = ???
}
