package running.pipeline

import running._
import spray.json.{JsObject, JsString, JsValue}
import sangria.ast.Document

case class Request(
    ctx: Context,
    graphQlQuery: Document,
    body: Option[JsObject],
    cookies: Map[String, String],
    url: String,
    hostname: String
) extends PipelineInput
    with PipelineOutput {

  type ModelId = String
  type FieldId = String
  type Resources = Map[ModelId, List[FieldId]]

  lazy val resources: Resources = ???
}
