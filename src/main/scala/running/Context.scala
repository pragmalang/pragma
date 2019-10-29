package running
import spray.json.{JsObject, JsString, JsValue}
import sangria.ast.Document

case class Context(
    data: Option[JsValue],
    graphQlQuery: Document,
    body: Option[JsObject],
    cookies: Map[String, String],
    url: String,
    hostname: String,
    user: Option[JwtPaylod]
)
