package domain.ops
import domain.primitives._
import domain._

object Add {
  def apply[V <: HValue](a: V, b: V): HValue = (a, b) match {
    case (a: HIntegerValue, b: HIntegerValue) =>
      HIntegerValue(a.value + b.value)
    case (a: HFloatValue, b: HFloatValue)   => HFloatValue(a.value + b.value)
    case (a: HStringValue, b: HStringValue) => HStringValue(a.value + b.value)
    case (a: HArrayValue[_], b: HArrayValue[_]) =>
      HArrayValue(a.values ++ b.values, a.elementType)
    case _ =>
      throw new Exception(
        s"Type error occured evaluating ${a.htype} + ${b.htype}"
      )
  }
}
