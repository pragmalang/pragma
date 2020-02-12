package running.execution

import running.pipeline._
import spray.json._
import domain._
import akka.stream.scaladsl.Source

object RequestHandler {
  def handle(
      request: Request,
      syntaxTree: SyntaxTree,
      responseTransformer: Response => Response
  ): Either[Response, Source[Response, _]] = ???
}

sealed trait RequestMatcher {
  def matches(request: Request): Boolean
}

object RequestMatcher {
  val default = List(
    CreateMatcher
  )
}

object CreateMatcher extends RequestMatcher {
  override def matches(request: Request): Boolean = ???
}