package domain.ops
import domain.primitives._
import domain._

object Add {
  trait Add[A] {
    def add(a: A, b: A): A
  }

  def apply[A](a: A, b: A)(implicit adder: Add[A]): A = adder.add(a, b)

  implicit val hstrAdd: Add[HStringValue] = new Add[HStringValue] {
    def add(a: HStringValue, b: HStringValue) = HStringValue(a.value + b.value)
  }

  implicit val hintAdd: Add[HIntegerValue] = new Add[HIntegerValue] {
    def add(a: HIntegerValue, b: HIntegerValue) =
      HIntegerValue(a.value + b.value)
  }

  implicit val hfloatAdd: Add[HFloatValue] = new Add[HFloatValue] {
    def add(a: HFloatValue, b: HFloatValue) = HFloatValue(a.value + b.value)
  }

  def harrayAddGen[A <: HValue](elemType: HType): Add[HArrayValue[A]] =
    new Add[HArrayValue[A]] {
      def add(a: HArrayValue[A], b: HArrayValue[A]) =
        HArrayValue(a.values ::: b.values, HArray(elemType))
    }
  implicit val integerHArrayAdd = harrayAddGen[HIntegerValue](HInteger)
  implicit val floatHArrayAdd = harrayAddGen[HFloatValue](HFloat)
  implicit val stringHArrayAdd = harrayAddGen[HStringValue](HString)
  implicit val boolHArrayAdd = harrayAddGen[HBoolValue](HBool)
}
