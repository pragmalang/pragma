package domain.ops.arithmetics
import domain.primitives._
import domain.utils._

case object Mod extends HOperation2 {
  def apply[V <: HValue](a: V, b: V): HValue = (a, b) match {
    case (a: HIntegerValue, b: HIntegerValue) =>
      HIntegerValue(a.value % b.value)
    case (a: HFloatValue, b: HFloatValue)   => HFloatValue(a.value % b.value)
    case (a: HIntegerValue, b: HFloatValue) => HFloatValue(a.value % b.value)
    case (a: HFloatValue, b: HIntegerValue) => HFloatValue(a.value % b.value)
    case _ =>
      throw new InternalException(
        s"Type error occured evaluating ${a.htype} ($a) % ${b.htype} ($b)"
      )
  }
}
