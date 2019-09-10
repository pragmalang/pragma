package domain.ops.arithmetics
import domain.primitives._
import domain._
import domain.utils._

case object Add extends HOperation2 {
  override def apply[V <: HValue](a: V, b: V): HValue = (a, b) match {
    case (a: HIntegerValue, b: HIntegerValue) =>
      HIntegerValue(a.value + b.value)
    case (a: HFloatValue, b: HFloatValue)   => HFloatValue(a.value + b.value)
    case (a: HIntegerValue, b: HFloatValue) => HFloatValue(a.value + b.value)
    case (a: HFloatValue, b: HIntegerValue) => HFloatValue(a.value + b.value)
    case (a: HStringValue, b: HStringValue) => HStringValue(a.value + b.value)
    case (a: HArrayValue[_], b: HArrayValue[_]) =>
      HArrayValue(a.values ++ b.values, a.elementType)
    case (a: HArrayValue[_], b: HValue) if b.htype == a.elementType =>
      HArrayValue(a.values :+ b, a.elementType)
    case (a: HValue, b: HArrayValue[_]) if b.elementType == a.htype =>
      HArrayValue(a :: b.values, b.elementType)
    case (a: HStringValue, b: HIntegerValue) => HStringValue(a.value + b.value)
    case (a: HIntegerValue, b: HStringValue) => HStringValue(a.value + b.value)
    case (a: HStringValue, b: HFloatValue)   => HStringValue(a.value + b.value)
    case (a: HFloatValue, b: HStringValue)   => HStringValue(a.value + b.value)
    case _ =>
      throw new InternalException(
        s"Type error occured evaluating ${a.htype} ($a) + ${b.htype} ($b)"
      )
  }
}
