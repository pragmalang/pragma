package running

import sangria.ast._
import spray.json._
import domain.utils.InternalException

package object utils {

  def constrainedRandom[U](cond: U => Boolean, generator: () => U): U = {
    val r = generator()
    if (cond(r)) r else constrainedRandom(cond, generator)
  }

  class QueryError(message: String) extends Exception(s"QueryError: $message")

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

  def objFieldsFrom(
      args: Vector[sangria.ast.Argument]
  ): Vector[(String, spray.json.JsValue)] =
    args.map(arg => arg.name -> sangriaToJson(arg.value))

}
