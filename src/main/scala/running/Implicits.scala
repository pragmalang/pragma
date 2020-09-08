package running

import domain._
import spray.json._
import spray.json.DefaultJsonProtocol._
import sangria.ast._
import sangria.parser.QueryParser
import domain.utils.InternalException

object RunningImplicits {

  implicit object PValueJsonWriter extends JsonWriter[PValue] {
    override def write(obj: PValue): JsValue = obj match {
      case PStringValue(value) => JsString(value)
      case PIntValue(value)    => JsNumber(value)
      case PFloatValue(value)  => JsNumber(value)
      case PBoolValue(value)   => JsBoolean(value)
      case PDateValue(value)   => JsString(value.toString())
      case PArrayValue(values, _) =>
        JsArray(values.map(PValueJsonWriter.write(_)).toVector)
      case PFileValue(value, _) => JsString(value.toString())
      case PModelValue(value, _) =>
        JsObject(value.map {
          case (key, value) => (key, PValueJsonWriter.write(value))
        })
      case PInterfaceValue(value, _) =>
        JsObject(value.map {
          case (key, value) => (key, PValueJsonWriter.write(value))
        })
      case _: PFunctionValue[_, _] =>
        throw new SerializationException(
          "Pragma functions are not serializable"
        )
      case POptionValue(value, _) =>
        value.map(PValueJsonWriter.write(_)).getOrElse(JsNull)
    }
  }

  implicit object GraphQlValueJsonFormater extends JsonFormat[Value] {
    override def read(json: JsValue): Value = json match {
      case JsObject(fields) =>
        ObjectValue(
          fields
            .map(field => ObjectField(field._1, read(field._2)))
            .toVector
        )
      case JsArray(elements)                 => ListValue(elements.map(read))
      case JsString(value)                   => StringValue(value)
      case JsNumber(value) if value.isWhole  => BigIntValue(value.toBigInt)
      case JsNumber(value) if !value.isWhole => BigDecimalValue(value)
      case JsTrue                            => BooleanValue(true)
      case JsFalse                           => BooleanValue(false)
      case JsNull                            => NullValue()
    }
    override def write(obj: Value): JsValue = obj match {
      case ListValue(values, _, _) =>
        JsArray(values.map(write).toJson)
      case ObjectValue(fields, _, _) =>
        JsObject(fields.map(field => field.name -> write(field.value)).toMap)
      case BigDecimalValue(value, _, _) => value.toJson
      case BigIntValue(value, _, _)     => value.toJson
      case IntValue(value, _, _)        => value.toJson
      case FloatValue(value, _, _)      => value.toJson
      case BooleanValue(value, _, _)    => value.toJson
      case StringValue(value, _, _, _, _) =>
        value.toJson
      case EnumValue(value, _, _) => value.toJson
      case VariableValue(_, _, _) =>
        throw new InternalException(
          "GraphQL variable values cannot be serialized. They must be substituted first."
        )
      case NullValue(_, _) => JsNull
    }
  }

  implicit object JwtPaylodJsonFormater extends JsonFormat[JwtPayload] {
    def read(json: JsValue): JwtPayload = JwtPayload(
      json.asJsObject.fields("userId"),
      json.asJsObject.fields("role").convertTo[String]
    )

    def write(obj: JwtPayload): JsValue =
      JsObject("userId" -> obj.userId, "role" -> JsString(obj.role))
  }

  implicit object RequestJsonFormater extends JsonFormat[Request] {
    def read(json: JsValue): Request = Request(
      hookData = Some(json.asJsObject.fields("hookData")),
      body = Some(json.asJsObject.fields("body").asJsObject),
      query = QueryParser
        .parse(json.asJsObject.fields("body").asInstanceOf[JsString].value)
        .get,
      queryVariables = json.asJsObject.fields("queryVariables") match {
        case obj: JsObject => obj
        case _ =>
          throw DeserializationException(
            "Query variables must only be an Array or Object"
          )
      },
      cookies = json.asJsObject.fields("cookies").convertTo[Map[String, String]],
      url = json.asJsObject.fields("url").convertTo[String],
      hostname = json.asJsObject.fields("hostname").convertTo[String],
      user = json.asJsObject.fields("user").convertTo[Option[JwtPayload]]
    )
    def write(obj: Request): JsValue = JsObject(
      "hookData" -> obj.hookData.toJson,
      "body" -> obj.body.toJson,
      "kind" -> "Request".toJson,
      "query" -> obj.query.renderPretty.toJson,
      "queryVariables" -> obj.queryVariables,
      "cookies" -> obj.cookies.toJson,
      "url" -> obj.url.toJson,
      "hostname" -> obj.hostname.toJson,
      "user" -> obj.user.toJson,
      "kind" -> "Context".toJson
    )
  }
}
