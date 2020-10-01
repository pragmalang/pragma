package pragma.domain

import spray.json._
import spray.json.DefaultJsonProtocol._
import org.graalvm.polyglot
import scala.util.{Try, Success, Failure}
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

  implicit object GraalValueJsonFormater
      extends JsonWriter[polyglot.Value]
      with JsonReader[polyglot.Value] {

    import polyglot._

    @throws[InternalException]
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
          yield write(gval.getArrayElement(i.toLong))
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
        throw InternalException(
          s"Invalid value `$gval` of type `${gval.getClass.getCanonicalName}` received from Graal"
        )
    }

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

  implicit val jsValueEq = new Eq[JsValue] {
    def eqv(x: JsValue, y: JsValue): Boolean = x == y
  }

}
