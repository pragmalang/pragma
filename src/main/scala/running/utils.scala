package running

import sangria.ast._
import spray.json._
import domain.utils.InternalException

package utils {

  case class QueryError(messages: Vector[String])
      extends Exception(s"Query Error:\n${messages.mkString("; ")}")

  object QueryError {
    def apply(message: String) = new QueryError(Vector(message))
  }

  case object InvalidJwtTokenError extends Exception("Invalid JWT token")

}
package object utils {

  val VariableCoercionError = QueryError("")

  def constrainedRandom[U](cond: U => Boolean, generator: () => U): U = {
    val r = generator()
    if (cond(r)) r else constrainedRandom(cond, generator)
  }

  def sangriaToJson(v: sangria.ast.Value): JsValue = v match {
    case BigDecimalValue(value, _, _)   => JsNumber(value.toDouble)
    case BigIntValue(value, _, _)       => JsNumber(value.toLong)
    case BooleanValue(value, _, _)      => JsBoolean(value)
    case StringValue(value, _, _, _, _) => JsString(value)
    case EnumValue(value, _, _)         => JsString(value)
    case FloatValue(value, _, _)        => JsNumber(value)
    case IntValue(value, _, _)          => JsNumber(value)
    case NullValue(_, _)                => JsNull
    case ListValue(values, _, _)        => JsArray(values.map(sangriaToJson))
    case ObjectValue(fields, _, _) =>
      JsObject(fields.map { field =>
        field.name -> sangriaToJson(field.value)
      }.toMap)
    case VariableValue(name, _, _) =>
      throw InternalException(
        s"Variable $name should have been substituted at query reduction"
      )
  }

  def jsonToSangria(v: JsValue): Value = v match {
    case JsObject(fields) =>
      ObjectValue(
        fields.map { f =>
          ObjectField(f._1, jsonToSangria(f._2))
        }.toVector
      )
    case JsArray(elements) => ListValue(elements.map(jsonToSangria))
    case JsString(value)   => StringValue(value)
    case JsNumber(value) if value.isWhole =>
      BigIntValue(value.bigDecimal.toBigInteger())
    case JsNumber(value) =>
      BigDecimalValue(value.bigDecimal)
    case JsTrue  => BooleanValue(true)
    case JsFalse => BooleanValue(false)
    case JsNull  => NullValue()
  }

  def objFieldsFrom(
      args: Vector[sangria.ast.Argument]
  ): Vector[(String, spray.json.JsValue)] =
    args.map(arg => arg.name -> sangriaToJson(arg.value))

}
