package domain

import spray.json._
import spray.json.DefaultJsonProtocol._
import org.graalvm.polyglot
import scala.util.{Try, Success, Failure}

package object Implicits {

  import domain.utils.InternalException

  import running.JwtPaylod
  implicit object HvalueJsonFormater extends JsonWriter[PValue] {

    @throws[Error]
    def write(value: PValue): JsValue = value match {
      case f: ExternalFunction =>
        JsObject("id" -> JsString(f.id), "filePath" -> JsString(f.filePath))
      case PIntValue(value)   => value.toJson
      case PFloatValue(value) => value.toJson
      case _: PFunctionValue[_, _] =>
        throw new Error("Functions are not serializable")
      case PDateValue(value) => value.toString.toJson
      case PBoolValue(value) => value.toJson
      case POptionValue(value, valueType) =>
        value match {
          case None        => JsNull
          case Some(value) => write(value)
        }
      case PModelValue(value, ptype) =>
        value.map(field => field._1 -> write(field._2)).toMap.toJson
      case PInterfaceValue(value, ptype) =>
        value.map(field => field._1 -> write(field._2)).toMap.toJson
      case PFileValue(value, ptype)         => value.toPath.toUri.toString.toJson
      case PStringValue(value)              => value.toJson
      case PArrayValue(values, elementType) => values.map(write).toJson
    }
  }

  implicit class StringMethods(s: String) {
    import sys.process._
    def small = if (s.isEmpty) s else s.updated(0, s.head.toLower)
    def decodeJwt = JwtPaylod.decode(s)
    def $(msg: String, logsHandler: String => Unit = _ => ()) =
      s ! ProcessLogger(logsHandler) match {
        case 1 => Failure(new Exception(msg))
        case 0 => Success(())
      }

    def indent(by: Int, indentationChar: String = "  ") =
      s.prependedAll(indentationChar * by)

    def withQuotes: String = "\"" + s + "\""
  }

  implicit object GraalValueJsonFormater
      extends JsonWriter[polyglot.Value]
      with JsonReader[polyglot.Value] {

    import polyglot._

    @throws[Error]
    def write(gval: polyglot.Value): JsValue = {
      // Number
      if (gval.isNumber) JsNumber(gval.asDouble)
      // Boolean
      else if (gval.isBoolean) JsBoolean(gval.asBoolean)
      // Null
      else if (gval.isNull) JsNull
      // String
      else if (gval.isString) JsString(gval.asString)
      // Array
      else if (Try(gval.getArraySize).isSuccess) JsArray {
        val jsElements = for (i <- 0 until gval.getArraySize.toInt)
          yield write(gval.getArrayElement(i))
        jsElements.toVector
      }
      // Date
      else if (gval.isDate) JsString(gval.toString)
      // Object
      else if (gval.hasMembers) {
        val keys = gval.getMemberKeys().toArray().map(_.toString)
        val values = keys.map(gval.getMember(_))
        JsObject(keys.zip(values.map(write)).toMap)
      }
      // Other
      else
        throw new InternalException(
          s"Invalid value `$gval` of type `${gval.getClass.getCanonicalName}` received from Graal"
        )
    }

    @throws[Error]
    def read(json: JsValue): polyglot.Value = json match {
      case JsArray(elements) => Value.asValue(elements.map(read).toArray)
      case JsTrue            => Value.asValue(true)
      case JsFalse           => Value.asValue(false)
      case JsNull            => Value.asValue(null)
      case JsNumber(value)   => Value.asValue(value.toDouble)
      case JsString(value)   => Value.asValue(value)
      case JsObject(fields) => {
        val entries = new java.util.HashMap[String, Object]()
        fields.foreach {
          case (key, value) => entries.put(key, read(value))
        }
        Value.asValue(entries)
      }
    }
  }
}
