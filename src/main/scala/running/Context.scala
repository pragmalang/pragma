package running
import spray.json.JsValue
import sangria.ast.Document

case class Context(
    data: JsValue,
    graphQlQuery: Document,
    body: JsValue,
    cookies: JsValue,
    url: String,
    hostname: String
)
