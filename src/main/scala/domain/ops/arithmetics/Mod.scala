package domain.ops.arithmetics
import domain.primitives._
import domain.utils._
import domain._

case object Mod extends HOperation {
  override val arity = 2
  override def apply(args: List[HValue]): HValue = args match {
    case List(a: HIntegerValue, b: HIntegerValue) =>
      HIntegerValue(a.value % b.value)
    case List(a: HFloatValue, b: HFloatValue) => HFloatValue(a.value % b.value)
    case List(a: HIntegerValue, b: HFloatValue) =>
      HFloatValue(a.value % b.value)
    case List(a: HFloatValue, b: HIntegerValue) =>
      HFloatValue(a.value % b.value)
    case List(a, b) =>
      throw new InternalException(
        s"Type error occured evaluating ${a.htype} ($a) % ${b.htype} ($b)"
      )
  }
}
