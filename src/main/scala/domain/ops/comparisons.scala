package domain.ops.comparisons
import domain.primitives._
import domain.utils._
import domain._

case object Gt extends HOperation {
  override val arity = 2
  override def apply(args: List[HValue]): HValue = args match {
    case List(a: HIntegerValue, b: HIntegerValue) => HBoolValue(a.value > b.value)
    case List(a: HFloatValue, b: HFloatValue)     => HBoolValue(a.value > b.value)
    case List(a: HIntegerValue, b: HFloatValue)   => HBoolValue(a.value > b.value)
    case List(a: HFloatValue, b: HIntegerValue)   => HBoolValue(a.value > b.value)
    case List(a, b) =>
      throw new TypeMismatchException(List(HInteger, HFloat), a.htype)
  }
}
case object Gte extends HOperation {
  override val arity = 2
  override def apply(args: List[HValue]): HValue = args match {
    case List(a: HIntegerValue, b: HIntegerValue) => HBoolValue(a.value >= b.value)
    case List(a: HFloatValue, b: HFloatValue)     => HBoolValue(a.value >= b.value)
    case List(a: HIntegerValue, b: HFloatValue)   => HBoolValue(a.value >= b.value)
    case List(a: HFloatValue, b: HIntegerValue)   => HBoolValue(a.value >= b.value)
    case List(a, b) =>
      throw new TypeMismatchException(List(HInteger, HFloat), a.htype)
  }
}
case object Lt extends HOperation {
  override val arity = 2
  override def apply(args: List[HValue]): HValue = args match {
    case List(a: HIntegerValue, b: HIntegerValue) => HBoolValue(a.value < b.value)
    case List(a: HFloatValue, b: HFloatValue)     => HBoolValue(a.value < b.value)
    case List(a: HIntegerValue, b: HFloatValue)   => HBoolValue(a.value < b.value)
    case List(a: HFloatValue, b: HIntegerValue)   => HBoolValue(a.value < b.value)
    case List(a, b) =>
      throw new TypeMismatchException(List(HInteger, HFloat), a.htype)
  }
}
case object Lte extends HOperation {
  override val arity = 2
  override def apply(args: List[HValue]): HValue = args match {
    case List(a: HIntegerValue, b: HIntegerValue) => HBoolValue(a.value <= b.value)
    case List(a: HFloatValue, b: HFloatValue)     => HBoolValue(a.value <= b.value)
    case List(a: HIntegerValue, b: HFloatValue)   => HBoolValue(a.value <= b.value)
    case List(a: HFloatValue, b: HIntegerValue)   => HBoolValue(a.value <= b.value)
    case List(a, b) =>
      throw new TypeMismatchException(List(HInteger, HFloat), a.htype)
  }
}
case object Eq extends HOperation {
  override val arity = 2
  override def apply(args: List[HValue]): HValue = args match {
    case List(a: HIntegerValue, b: HIntegerValue) => HBoolValue(a.value == b.value)
    case List(a: HFloatValue, b: HFloatValue)     => HBoolValue(a.value == b.value)
    case List(a: HIntegerValue, b: HFloatValue)   => HBoolValue(a.value == b.value)
    case List(a: HFloatValue, b: HIntegerValue)   => HBoolValue(a.value == b.value)
    case List(a, b) =>
      throw new TypeMismatchException(List(HInteger, HFloat), a.htype)
  }
}
case object Neq extends HOperation {
  override val arity = 2
  override def apply(args: List[HValue]): HValue = args match {
    case List(a: HIntegerValue, b: HIntegerValue) => HBoolValue(a.value != b.value)
    case List(a: HFloatValue, b: HFloatValue)     => HBoolValue(a.value != b.value)
    case List(a: HIntegerValue, b: HFloatValue)   => HBoolValue(a.value != b.value)
    case List(a: HFloatValue, b: HIntegerValue)   => HBoolValue(a.value != b.value)
    case List(a, b) =>
      throw new TypeMismatchException(List(HInteger, HFloat), a.htype)
  }
}
