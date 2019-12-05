package running.pipeline
import spray.json.JsValue

trait Response extends PipelineOutput with Cloneable {
  val responseCode: Int
  val body: JsValue
}

case class HttpErrorResponse(responseCode: Int, body: JsValue) extends Response

case class BaseResponse(responseCode: Int, body: JsValue) extends Response
