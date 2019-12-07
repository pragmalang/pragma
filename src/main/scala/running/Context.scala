package running
import spray.json.{JsObject, JsString, JsValue}
import sangria.ast.Document

case class Context(
    user: Option[JwtPaylod],
    graphQlQuery: Document,
    cookies: Map[String, String],
    url: String,
    hostname: String
)
