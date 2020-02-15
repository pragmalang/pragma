package running.pipeline

import spray.json._
import domain._

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
