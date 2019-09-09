package domain.ops
import domain.primitives._
import domain._
import domain.utils._

case object Sub extends HOperation {
  def apply[V <: HValue](a: V, b: V): HValue = (a, b) match {
    case (a: HIntegerValue, b: HIntegerValue) =>
      HIntegerValue(a.value - b.value)
    case (a: HFloatValue, b: HFloatValue)   => HFloatValue(a.value - b.value)
    case (a: HIntegerValue, b: HFloatValue) => HFloatValue(a.value - b.value)
    case (a: HFloatValue, b: HIntegerValue) => HFloatValue(a.value - b.value)
    case _ =>
      throw new InternalException(
        s"Type error occured while evaluating ${a.htype} ($a) - ${b.htype} ($b)"
      )
  }
}
