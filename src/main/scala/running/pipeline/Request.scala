package running.pipeline

import running._
import spray.json.{JsObject, JsString, JsValue}
import domain._
import domain.primitives._
import sangria.ast._
import scala.util.Try

case class Request(
    data: Option[JsValue],
    ctx: RequestContext,
    body: Option[JsObject],
    syntaxTree: Option[SyntaxTree] = None,
) extends PipelineInput
    with PipelineOutput