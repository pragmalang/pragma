package domain.ops.arithmetics
import domain.primitives._
import domain._
import domain.utils._

case object Sub extends HOperation {
  override val arity = 2

  override def apply(args: List[HValue]): HValue = args match {
    case List(a: HIntegerValue, b: HIntegerValue) =>
      HIntegerValue(a.value - b.value)
    case List(a: HFloatValue, b: HFloatValue)   => HFloatValue(a.value - b.value)
    case List(a: HIntegerValue, b: HFloatValue) => HFloatValue(a.value - b.value)
    case List(a: HFloatValue, b: HIntegerValue) => HFloatValue(a.value - b.value)
    case List(a, b) =>
      throw new InternalException(
        s"Type error occured while evaluating ${a.htype} ($a) - ${b.htype} ($b)"
      )
  }
}
