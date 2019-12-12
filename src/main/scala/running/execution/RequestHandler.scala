package running.execution

import running.pipeline._
import spray.json._
import domain._

sealed trait RequestHandler {
  def matcher(request: Request): Boolean
  def handler(
      request: Request,
      syntaxTree: SyntaxTree,
      responseTransformer: Response => Response
  ): Response
}

object RequestHandler {
  val defaultHandlers = List(
    CreateRequestHandler
  )
}

object CreateRequestHandler extends RequestHandler {
  override def matcher(request: Request): Boolean = ???
  override def handler(
      request: Request,
      syntaxTree: SyntaxTree,
      responseTransformer: Response => Response
  ): Response = BaseResponse(200, JsNull)
}