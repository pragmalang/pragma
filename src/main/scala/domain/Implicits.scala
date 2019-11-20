package domain

import spray.json._
import spray.json.DefaultJsonProtocol._
import primitives._

package object Implicits {
  implicit object HvalueJsonFormater extends JsonWriter[HValue] {
    @throws[Error]
    def write(value: HValue): JsValue = value match {
      case f: ExternalFunction =>
        JsObject("id" -> JsString(f.id), "filePath" -> JsString(f.filePath))
      case HIntegerValue(value) => value.toJson
      case HFloatValue(value)   => value.toJson
      case HFunctionValue(body, htype) =>
        throw new Error("Functions are not serializable")
      case HDateValue(value) => value.toString.toJson
      case HBoolValue(value) => value.toJson
      case HOptionValue(value, valueType) =>
        value match {
          case None        => JsNull
          case Some(value) => write(value)
        }
      case HModelValue(value, htype) =>
        value.map(field => field._1 -> write(field._2)).toMap.toJson
      case HInterfaceValue(value, htype) =>
        value.map(field => field._1 -> write(field._2)).toMap.toJson
      case HFileValue(value, htype)         => value.toPath.toUri.toString.toJson
      case HStringValue(value)              => value.toJson
      case HArrayValue(values, elementType) => values.map(write(_)).toJson
    }
  }
}
