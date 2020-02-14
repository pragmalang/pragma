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
    body: Option[JsObject]
) extends PipelineInput
    with PipelineOutput

object Request {
  lazy val hType: HInterface = HInterface(
    "Request",
    List(
      HInterfaceField("data", HAny, None),
      HInterfaceField("ctx", RequestContext.hType, None),
      HInterfaceField("body", HAny, None)
    ),
    None
  )
}
