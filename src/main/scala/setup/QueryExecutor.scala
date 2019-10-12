package setup
import domain.SyntaxTree
import sangria.ast.Document

case class QueryExecutor[Request](
    schema: SyntaxTree,
    query: Document,
    storage: Storage
)
