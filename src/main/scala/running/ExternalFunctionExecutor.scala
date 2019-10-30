package running
import spray.json.JsValue
sealed trait ExternalFunctionExecutor {
  def execute(): JsValue
}

object DockerFunctionExecutor extends ExternalFunctionExecutor {
  def execute(): JsValue = ???
}
