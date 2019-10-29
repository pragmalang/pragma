package running
import spray.json.{JsObject, JsString, JsValue}
import sangria.ast.Document

case class Context(
    data: Option[JsValue],
    user: Option[JwtPaylod]
)
