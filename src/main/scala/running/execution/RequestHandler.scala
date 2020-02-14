package running.execution

import running.pipeline._
import spray.json._
import domain._
import akka.stream.scaladsl.Source

object RequestHandler {
  def handle(
      request: Request,
      syntaxTree: SyntaxTree
  ): Either[Response, Source[Response, _]] = ???
}
