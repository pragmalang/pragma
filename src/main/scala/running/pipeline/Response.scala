package running.pipeline
import spray.json.JsValue

trait Response extends PipelineOutput {
  val responseCode: Int
  val body: JsValue
}

case class HttpErrorResponse(responseCode: Int, body: JsValue) extends Response

case class BaseResponse(responseCode: Int, body: JsValue) extends Response
