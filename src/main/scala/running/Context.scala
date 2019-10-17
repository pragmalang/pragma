package running
import spray.json.{JsObject, JsString}
import sangria.ast.Document

case class Context(
    data: Option[JsObject],
    graphQlQuery: Document,
    body: Option[JsObject],
    cookies: Map[String, String],
    url: String,
    hostname: String,
    user: Option[JwtPaylod]
)
