package domain.ops.comparisons
import domain.primitives._
import domain.utils._

object Gt extends HOperation {
  def apply(a: HValue, b: HValue): HBoolValue = (a, b) match {
    case (a: HIntegerValue, b: HIntegerValue) => HBoolValue(a.value > b.value)
    case (a: HFloatValue, b: HFloatValue)     => HBoolValue(a.value > b.value)
    case (a: HIntegerValue, b: HFloatValue)   => HBoolValue(a.value > b.value)
    case (a: HFloatValue, b: HIntegerValue)   => HBoolValue(a.value > b.value)
    case (a, b) =>
      throw new TypeMismatchException(List(HInteger, HFloat), a.htype)
  }
}
object Gte extends HOperation {
  def apply(a: HValue, b: HValue): HBoolValue = (a, b) match {
    case (a: HIntegerValue, b: HIntegerValue) => HBoolValue(a.value >= b.value)
    case (a: HFloatValue, b: HFloatValue)     => HBoolValue(a.value >= b.value)
    case (a: HIntegerValue, b: HFloatValue)   => HBoolValue(a.value >= b.value)
    case (a: HFloatValue, b: HIntegerValue)   => HBoolValue(a.value >= b.value)
    case (a, b) =>
      throw new TypeMismatchException(List(HInteger, HFloat), a.htype)
  }
}
object Lt extends HOperation {
  def apply(a: HValue, b: HValue): HBoolValue = (a, b) match {
    case (a: HIntegerValue, b: HIntegerValue) => HBoolValue(a.value < b.value)
    case (a: HFloatValue, b: HFloatValue)     => HBoolValue(a.value < b.value)
    case (a: HIntegerValue, b: HFloatValue)   => HBoolValue(a.value < b.value)
    case (a: HFloatValue, b: HIntegerValue)   => HBoolValue(a.value < b.value)
    case (a, b) =>
      throw new TypeMismatchException(List(HInteger, HFloat), a.htype)
  }
}
object Lte extends HOperation {
  def apply(a: HValue, b: HValue): HBoolValue = (a, b) match {
    case (a: HIntegerValue, b: HIntegerValue) => HBoolValue(a.value <= b.value)
    case (a: HFloatValue, b: HFloatValue)     => HBoolValue(a.value <= b.value)
    case (a: HIntegerValue, b: HFloatValue)   => HBoolValue(a.value <= b.value)
    case (a: HFloatValue, b: HIntegerValue)   => HBoolValue(a.value <= b.value)
    case (a, b) =>
      throw new TypeMismatchException(List(HInteger, HFloat), a.htype)
  }
}
object Eq extends HOperation {
  def apply(a: HValue, b: HValue): HBoolValue = (a, b) match {
    case (a: HIntegerValue, b: HIntegerValue) => HBoolValue(a.value == b.value)
    case (a: HFloatValue, b: HFloatValue)     => HBoolValue(a.value == b.value)
    case (a: HIntegerValue, b: HFloatValue)   => HBoolValue(a.value == b.value)
    case (a: HFloatValue, b: HIntegerValue)   => HBoolValue(a.value == b.value)
    case (a, b) =>
      throw new TypeMismatchException(List(HInteger, HFloat), a.htype)
  }
}
object Neq extends HOperation {
  def apply(a: HValue, b: HValue): HBoolValue = (a, b) match {
    case (a: HIntegerValue, b: HIntegerValue) => HBoolValue(a.value != b.value)
    case (a: HFloatValue, b: HFloatValue)     => HBoolValue(a.value != b.value)
    case (a: HIntegerValue, b: HFloatValue)   => HBoolValue(a.value != b.value)
    case (a: HFloatValue, b: HIntegerValue)   => HBoolValue(a.value != b.value)
    case (a, b) =>
      throw new TypeMismatchException(List(HInteger, HFloat), a.htype)
  }
}
