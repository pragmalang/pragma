package running.execution

import running.pipeline._
import spray.json._
import domain._

sealed trait RequestHandler {
  val matcher: Matcher
  def handler(
      request: Request,
      syntaxTree: SyntaxTree,
      resultTransformer: Response => Response
  ): Response
}

object RequestHandler {
  val defaultHandlers = List(
    CreateRequestHandler
  )
}

object CreateRequestHandler extends RequestHandler {
  override val matcher: Matcher = CreateMatcher
  override def handler(
      request: Request,
      syntaxTree: SyntaxTree,
      resultTransformer: Response => Response
  ): Response = BaseResponse(200, JsNull)
}