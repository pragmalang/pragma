package domain.ops.arithmetics
import domain.primitives._
import domain._
import domain.utils._

case object Add extends HOperation {
  override val arity = 2
  override def apply(args: List[HValue]): HValue = args match {
    case List(a: HIntegerValue, b: HIntegerValue) =>
      HIntegerValue(a.value + b.value)
    case List(a: HFloatValue, b: HFloatValue) => HFloatValue(a.value + b.value)
    case List(a: HIntegerValue, b: HFloatValue) =>
      HFloatValue(a.value + b.value)
    case List(a: HFloatValue, b: HIntegerValue) =>
      HFloatValue(a.value + b.value)
    case List(a: HStringValue, b: HStringValue) =>
      HStringValue(a.value + b.value)
    case List(a: HArrayValue[_], b: HArrayValue[_]) =>
      HArrayValue(a.values ++ b.values, a.elementType)
    case List(a: HArrayValue[_], b: HValue) if b.htype == a.elementType =>
      HArrayValue(a.values :+ b, a.elementType)
    case List(a: HValue, b: HArrayValue[_]) if b.elementType == a.htype =>
      HArrayValue(a :: b.values, b.elementType)
    case List(a: HStringValue, b: HIntegerValue) =>
      HStringValue(a.value + b.value)
    case List(a: HIntegerValue, b: HStringValue) =>
      HStringValue(a.value + b.value)
    case List(a: HStringValue, b: HFloatValue) =>
      HStringValue(a.value + b.value)
    case List(a: HFloatValue, b: HStringValue) =>
      HStringValue(a.value + b.value)
    case List(a, b) =>
      throw new InternalException(
        s"Type error occured evaluating ${a.htype} ($a) + ${b.htype} ($b)"
      )
  }
}
