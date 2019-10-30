package running
import spray.json.{JsValue, JsObject}
import scala.util.Try
import domain.primitives.{ExternalFunction}
import domain.HType
import org.graalvm.polyglot
import spray.json._

case class DockerFunction(id: String, filePath: String, htype: HType)
    extends ExternalFunction {
  override def execute(input: JsValue): Try[JsValue] = ???
}

case class GraalFunction(
    id: String,
    htype: HType,
    filePath: String,
    graalCtx: polyglot.Context,
    languageId: String
) extends ExternalFunction {
  val graalFunction = graalCtx.getBindings(languageId).getMember(id)

  override def execute(input: JsValue): Try[JsValue] =
    Try(graalValueToJsValue(graalFunction.execute(input)))

  def graalValueToJsValue(gval: polyglot.Value): JsValue = {
    // Number
    if (gval.isNumber) JsNumber(gval.asDouble)
    // Boolean
    else if (gval.isBoolean) JsBoolean(gval.asBoolean)
    // Null
    else if (gval.isNull) JsNull
    // Object
    else if (gval.isProxyObject || gval.isHostObject) {
      val keys = gval.getMemberKeys().toArray().map(_.toString)
      val values = keys.map(gval.getMember(_))
      JsObject(keys.zip(values.map(graalValueToJsValue)).toMap)
    }
    // String
    else if (gval.isString) JsString(gval.asString)
    // Array
    else if (Try(gval.getArraySize).isSuccess) JsArray {
      val jsElements = for (i <- 0 until gval.getArraySize.toInt)
        yield graalValueToJsValue(gval.getArrayElement(i))
      jsElements.toVector
    }
    // Date
    else if (gval.isDate) JsString(gval.toString)
    // Other
    else throw new Exception("Invalid value type")
  }
}
