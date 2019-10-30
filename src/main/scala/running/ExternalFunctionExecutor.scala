package running
import spray.json.JsValue
sealed trait ExternalFunctionExecutor {
  def execute(): JsValue
}

object DockerFunctionExecutor extends ExternalFunctionExecutor {
  override def execute(): JsValue = ???
}

object GraalFunctionExecutor extends ExternalFunctionExecutor {
  override def execute(): JsValue = ???
}
