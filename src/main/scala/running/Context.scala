package running
import spray.json.{JsValue, JsString}
import sangria.ast.Document

case class Context(
    data: JsValue,
    graphQlQuery: Document,
    body: JsValue,
    cookies: Map[String, String],
    url: String,
    hostname: String,
    user: Option[JwtPaylod]
)
