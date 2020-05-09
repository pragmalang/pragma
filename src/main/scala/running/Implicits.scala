package running

import spray.json._
import spray.json.DefaultJsonProtocol._
import running.pipeline._
import sangria.ast._
import sangria.parser.QueryParser
import domain.primitives._

package object Implicits {

  implicit object PValueJsonWriter extends JsonWriter[PValue] {
    override def write(obj: PValue): JsValue = obj match {
      case PStringValue(value) => JsString(value)
      case PIntValue(value)    => JsNumber(value)
      case PFloatValue(value)  => JsNumber(value)
      case PBoolValue(value)   => JsBoolean(value)
      case PDateValue(value)   => JsString(value.toString())
      case PArrayValue(values, elementType) =>
        JsArray(values.map(PValueJsonWriter.write(_)).toVector)
      case PFileValue(value, ptype) => JsString(value.toString())
      case PModelValue(value, ptype) =>
        JsObject(value.map {
          case (key, value) => (key, PValueJsonWriter.write(value))
        })
      case PInterfaceValue(value, ptype) =>
        JsObject(value.map {
          case (key, value) => (key, PValueJsonWriter.write(value))
        })
      case _: PFunctionValue[_, _] =>
        throw new SerializationException(
          "Pragma functions are not serializable"
        )
      case POptionValue(value, valueType) =>
        value.map(PValueJsonWriter.write(_)).getOrElse(JsNull)
    }
  }

  implicit object GraphQlValueJsonFormater extends JsonFormat[Value] {
    override def read(json: JsValue): Value = json match {
      case JsObject(fields) =>
        ObjectValue(
          fields
            .map(field => ObjectField(field._1, field._2.convertTo[Value]))
            .toVector
        )
      case JsArray(elements)                 => ListValue(elements.map(_.convertTo[Value]))
      case JsString(value)                   => StringValue(value)
      case JsNumber(value) if value.isWhole  => BigIntValue(value.toBigInt)
      case JsNumber(value) if !value.isWhole => BigDecimalValue(value)
      case JsTrue                            => BooleanValue(true)
      case JsFalse                           => BooleanValue(false)
      case JsNull                            => NullValue()
    }
    override def write(obj: Value): JsValue = obj match {
      case ListValue(values, comments, location) =>
        JsArray(values.map(_.toJson).toJson)
      case ObjectValue(fields, comments, location) =>
        JsObject(fields.map(field => field.name -> field.value.toJson).toMap)
      case BigDecimalValue(value, comments, location) => value.toJson
      case BigIntValue(value, comments, location)     => value.toJson
      case IntValue(value, comments, location)        => value.toJson
      case FloatValue(value, comments, location)      => value.toJson
      case BooleanValue(value, comments, location)    => value.toJson
      case StringValue(value, block, blockRawValue, comments, location) =>
        value.toJson
      case EnumValue(value, comments, location) => value.toJson
      case VariableValue(name, comments, location) =>
        throw new InternalError(
          "GraphQL variable values cannot be serialized. They must be substituted first."
        )
      case NullValue(comments, location) => JsNull
    }
  }

  implicit object JwtPaylodJsonFormater extends JsonFormat[JwtPaylod] {
    def read(json: JsValue): JwtPaylod = JwtPaylod(
      json.asJsObject.fields("userId").convertTo[String],
      json.asJsObject.fields("role").convertTo[String]
    )

    def write(obj: JwtPaylod): JsValue =
      JsObject("userId" -> JsString(obj.userId), "role" -> JsString(obj.role))
  }

  implicit object RequestJsonFormater extends JsonFormat[Request] {
    def read(json: JsValue): Request = Request(
      hookData = Some(json.asJsObject.fields("hookData")),
      body = Some(json.asJsObject.fields("body").asJsObject),
      query = QueryParser
        .parse(json.asJsObject.fields("body").asInstanceOf[JsString].value)
        .get,
      queryVariables = json.asJsObject.fields("queryVariables") match {
        case obj: JsObject => Left(obj)
        case arr: JsArray  => Right(arr.convertTo[List[JsObject]])
        case _ =>
          throw DeserializationException(
            "Query variables must only be an Array or Object"
          )
      },
      cookies = json.asJsObject.fields("cookies").convertTo[Map[String, String]],
      url = json.asJsObject.fields("url").convertTo[String],
      hostname = json.asJsObject.fields("hostname").convertTo[String],
      user = json.asJsObject.fields("user").convertTo[Option[JwtPaylod]]
    )
    def write(obj: Request): JsValue = JsObject(
      "hookData" -> obj.hookData.toJson,
      "body" -> obj.body.toJson,
      "kind" -> "Request".toJson,
      "query" -> obj.query.renderPretty.toJson,
      "queryVariables" -> (obj.queryVariables match {
        case Left(vars)  => vars.toJson
        case Right(vars) => vars.toJson
      }),
      "cookies" -> obj.cookies.toJson,
      "url" -> obj.url.toJson,
      "hostname" -> obj.hostname.toJson,
      "user" -> obj.user.toJson,
      "kind" -> "Context".toJson
    )
  }
}