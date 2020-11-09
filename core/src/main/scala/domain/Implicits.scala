package pragma.domain

import spray.json._
import spray.json.DefaultJsonProtocol._
import scala.util.{Success, Failure}
import pragma.domain.utils.InternalException
import cats.kernel.Eq

object DomainImplicits {

  implicit object PvalueJsonFormater extends JsonWriter[PValue] {
    @throws[InternalException]
    def write(value: PValue): JsValue = value match {
      case f: ExternalFunction =>
        JsObject("id" -> JsString(f.id), "filePath" -> JsString(f.filePath))
      case PIntValue(value)   => value.toJson
      case PFloatValue(value) => value.toJson
      case _: PFunctionValue =>
        throw InternalException("Functions are not serializable")
      case PDateValue(value) => value.toString.toJson
      case PBoolValue(value) => value.toJson
      case POptionValue(value, _) =>
        value match {
          case None        => JsNull
          case Some(value) => write(value)
        }
      case PModelValue(value, _) =>
        value.map(field => field._1 -> write(field._2)).toMap.toJson
      case PInterfaceValue(value, _) =>
        value.map(field => field._1 -> write(field._2)).toMap.toJson
      case PFileValue(value, _)   => value.toPath.toUri.toString.toJson
      case PStringValue(value)    => value.toJson
      case PArrayValue(values, _) => values.map(write).toJson
    }
  }

  implicit class StringMethods(s: String) {
    import sys.process._
    def small = if (s.isEmpty) s else s.updated(0, s.head.toLower)

    def $(msg: String, logsHandler: String => Unit = _ => ()) =
      s ! ProcessLogger(logsHandler) match {
        case 1 => Failure(new Exception(msg))
        case 0 => Success(())
      }

    def indent(by: Int, indentationChar: String = "  ") =
      s.prependedAll(indentationChar * by)

    def withQuotes: String = "\"" + s + "\""
  }

  implicit val jsValueEq = new Eq[JsValue] {
    def eqv(x: JsValue, y: JsValue): Boolean = x == y
  }

}
