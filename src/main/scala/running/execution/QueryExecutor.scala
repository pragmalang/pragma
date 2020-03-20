package running.execution

import domain.SyntaxTree
import setup.storage.Storage
import running.pipeline._, functions._
import scala.util.Try
import akka.stream.scaladsl.Source

case class QueryExecutor(
    syntaxTree: SyntaxTree,
    storage: Storage
) {
  val authorizer = Authorizer(syntaxTree, storage)
  val requestValidator = RequestValidator(syntaxTree)
  val requestReducer = RequestReducer(syntaxTree)

  def execute(request: Request): Try[Either[Response, Source[Response, _]]] = ???
}
