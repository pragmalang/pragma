package running
import spray.json.JsValue
import scala.util.Try
import domain.primitives.ExternalFunction
import domain.HType
import org.graalvm.polyglot
import spray.json._
import domain.Implicits.GraalValueJsonFormater

case class DockerFunction(id: String, filePath: String, htype: HType)
    extends ExternalFunction {
  override def execute(input: JsValue): Try[JsValue] = ???
}

case class GraalFunction(
    id: String,
    htype: HType,
    filePath: String,
    graalCtx: polyglot.Context,
    languageId: String = "js"
) extends ExternalFunction {
  val graalFunction = graalCtx.getBindings(languageId).getMember(id)

  override def execute(input: JsValue): Try[JsValue] = Try {
    GraalValueJsonFormater
      .write(graalFunction.execute(graalCtx.eval(languageId, s"($input)")))
  }
}
