package domain.ops.comparisons
import domain.primitives._
import domain.utils._

case object Gt extends HOperation2 {
  def apply[V <: HValue](a: V, b: V): HBoolValue = (a, b) match {
    case (a: HIntegerValue, b: HIntegerValue) => HBoolValue(a.value > b.value)
    case (a: HFloatValue, b: HFloatValue)     => HBoolValue(a.value > b.value)
    case (a: HIntegerValue, b: HFloatValue)   => HBoolValue(a.value > b.value)
    case (a: HFloatValue, b: HIntegerValue)   => HBoolValue(a.value > b.value)
    case (a, b) =>
      throw new TypeMismatchException(List(HInteger, HFloat), a.htype)
  }
}
case object Gte extends HOperation2 {
  def apply[V <: HValue](a: V, b: V): HBoolValue = (a, b) match {
    case (a: HIntegerValue, b: HIntegerValue) => HBoolValue(a.value >= b.value)
    case (a: HFloatValue, b: HFloatValue)     => HBoolValue(a.value >= b.value)
    case (a: HIntegerValue, b: HFloatValue)   => HBoolValue(a.value >= b.value)
    case (a: HFloatValue, b: HIntegerValue)   => HBoolValue(a.value >= b.value)
    case (a, b) =>
      throw new TypeMismatchException(List(HInteger, HFloat), a.htype)
  }
}
case object Lt extends HOperation2 {
  def apply[V <: HValue](a: V, b: V): HBoolValue = (a, b) match {
    case (a: HIntegerValue, b: HIntegerValue) => HBoolValue(a.value < b.value)
    case (a: HFloatValue, b: HFloatValue)     => HBoolValue(a.value < b.value)
    case (a: HIntegerValue, b: HFloatValue)   => HBoolValue(a.value < b.value)
    case (a: HFloatValue, b: HIntegerValue)   => HBoolValue(a.value < b.value)
    case (a, b) =>
      throw new TypeMismatchException(List(HInteger, HFloat), a.htype)
  }
}
case object Lte extends HOperation2 {
  def apply[V <: HValue](a: V, b: V): HBoolValue = (a, b) match {
    case (a: HIntegerValue, b: HIntegerValue) => HBoolValue(a.value <= b.value)
    case (a: HFloatValue, b: HFloatValue)     => HBoolValue(a.value <= b.value)
    case (a: HIntegerValue, b: HFloatValue)   => HBoolValue(a.value <= b.value)
    case (a: HFloatValue, b: HIntegerValue)   => HBoolValue(a.value <= b.value)
    case (a, b) =>
      throw new TypeMismatchException(List(HInteger, HFloat), a.htype)
  }
}
case object Eq extends HOperation2 {
  def apply[V <: HValue](a: V, b: V): HBoolValue = (a, b) match {
    case (a: HIntegerValue, b: HIntegerValue) => HBoolValue(a.value == b.value)
    case (a: HFloatValue, b: HFloatValue)     => HBoolValue(a.value == b.value)
    case (a: HIntegerValue, b: HFloatValue)   => HBoolValue(a.value == b.value)
    case (a: HFloatValue, b: HIntegerValue)   => HBoolValue(a.value == b.value)
    case (a, b) =>
      throw new TypeMismatchException(List(HInteger, HFloat), a.htype)
  }
}
case object Neq extends HOperation2 {
  def apply[V <: HValue](a: V, b: V): HBoolValue = (a, b) match {
    case (a: HIntegerValue, b: HIntegerValue) => HBoolValue(a.value != b.value)
    case (a: HFloatValue, b: HFloatValue)     => HBoolValue(a.value != b.value)
    case (a: HIntegerValue, b: HFloatValue)   => HBoolValue(a.value != b.value)
    case (a: HFloatValue, b: HIntegerValue)   => HBoolValue(a.value != b.value)
    case (a, b) =>
      throw new TypeMismatchException(List(HInteger, HFloat), a.htype)
  }
}
