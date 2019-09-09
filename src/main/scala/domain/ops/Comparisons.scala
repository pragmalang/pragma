package domain.ops
import domain.primitives._
import domain.utils._

object Comparisons {
  def gt(a: HValue, b: HValue): HBoolValue = (a, b) match {
    case (a: HIntegerValue, b: HIntegerValue) => HBoolValue(a.value > b.value)
    case (a: HFloatValue, b: HFloatValue)     => HBoolValue(a.value > b.value)
    case (a: HIntegerValue, b: HFloatValue)   => HBoolValue(a.value > b.value)
    case (a: HFloatValue, b: HIntegerValue)   => HBoolValue(a.value > b.value)
    case (a, b) =>
      throw new TypeMismatchException(List(HInteger, HFloat), a.htype)
  }
  def gte(a: HValue, b: HValue): HBoolValue = (a, b) match {
    case (a: HIntegerValue, b: HIntegerValue) => HBoolValue(a.value >= b.value)
    case (a: HFloatValue, b: HFloatValue)     => HBoolValue(a.value >= b.value)
    case (a: HIntegerValue, b: HFloatValue)   => HBoolValue(a.value >= b.value)
    case (a: HFloatValue, b: HIntegerValue)   => HBoolValue(a.value >= b.value)
    case (a, b) =>
      throw new TypeMismatchException(List(HInteger, HFloat), a.htype)
  }
  def lt(a: HValue, b: HValue): HBoolValue = (a, b) match {
    case (a: HIntegerValue, b: HIntegerValue) => HBoolValue(a.value < b.value)
    case (a: HFloatValue, b: HFloatValue)     => HBoolValue(a.value < b.value)
    case (a: HIntegerValue, b: HFloatValue)   => HBoolValue(a.value < b.value)
    case (a: HFloatValue, b: HIntegerValue)   => HBoolValue(a.value < b.value)
    case (a, b) =>
      throw new TypeMismatchException(List(HInteger, HFloat), a.htype)
  }
  def lte(a: HValue, b: HValue): HBoolValue = (a, b) match {
    case (a: HIntegerValue, b: HIntegerValue) => HBoolValue(a.value <= b.value)
    case (a: HFloatValue, b: HFloatValue)     => HBoolValue(a.value <= b.value)
    case (a: HIntegerValue, b: HFloatValue)   => HBoolValue(a.value <= b.value)
    case (a: HFloatValue, b: HIntegerValue)   => HBoolValue(a.value <= b.value)
    case (a, b) =>
      throw new TypeMismatchException(List(HInteger, HFloat), a.htype)
  }
  def equ(a: HValue, b: HValue): HBoolValue = (a, b) match {
    case (a: HIntegerValue, b: HIntegerValue) => HBoolValue(a.value == b.value)
    case (a: HFloatValue, b: HFloatValue)     => HBoolValue(a.value == b.value)
    case (a: HIntegerValue, b: HFloatValue)   => HBoolValue(a.value == b.value)
    case (a: HFloatValue, b: HIntegerValue)   => HBoolValue(a.value == b.value)
    case (a, b) =>
      throw new TypeMismatchException(List(HInteger, HFloat), a.htype)
  }
  def neq(a: HValue, b: HValue): HBoolValue = (a, b) match {
    case (a: HIntegerValue, b: HIntegerValue) => HBoolValue(a.value != b.value)
    case (a: HFloatValue, b: HFloatValue)     => HBoolValue(a.value != b.value)
    case (a: HIntegerValue, b: HFloatValue)   => HBoolValue(a.value != b.value)
    case (a: HFloatValue, b: HIntegerValue)   => HBoolValue(a.value != b.value)
    case (a, b) =>
      throw new TypeMismatchException(List(HInteger, HFloat), a.htype)
  }
}
