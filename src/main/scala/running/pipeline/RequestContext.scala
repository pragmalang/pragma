package running.pipeline

import spray.json.{JsObject, JsString, JsValue}
import sangria.ast.Document
import running._

case class RequestContext(
    user: Option[JwtPaylod],
    graphQlQuery: Document,
    cookies: Map[String, String],
    url: String,
    hostname: String
)
