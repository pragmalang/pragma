package domain.ops
import domain.primitives._
import domain._

object Add {
  trait Add[A] {
    def add(a: A, b: A): A
  }

  def apply[A](a: A, b: A)(implicit adder: Add[A]): A = adder.add(a, b)
  type HStrVal = HValue[String]
  implicit val hstrAdd: Add[HStrVal] = new Add[HStrVal] {
    def add(a: HStrVal, b: HStrVal) = HValue(a.value + b.value, HString)
  }

  type HIntVal = HValue[Long]
  implicit val hintAdd: Add[HIntVal] = new Add[HIntVal] {
    def add(a: HIntVal, b: HIntVal) = HValue(a.value + b.value, HInteger)
  }

  type HFloatVal = HValue[Double]
  implicit val hfloatAdd: Add[HFloatVal] = new Add[HFloatVal] {
    def add(a: HFloatVal, b: HFloatVal) = HValue(a.value + b.value, HFloat)
  }

  def harrayAddGen[A](elemType: HType): Add[HValue[List[A]]] =
    new Add[HValue[List[A]]] {
      def add(a: HValue[List[A]], b: HValue[List[A]]) =
        HValue(a.value ::: b.value, HArray(elemType))
    }
  implicit val integerHArrayAdd = harrayAddGen[Long](HInteger)
  implicit val floatHArrayAdd = harrayAddGen[Double](HFloat)
  implicit val stringHArrayAdd = harrayAddGen[String](HString)
  implicit val boolHArrayAdd = harrayAddGen[Boolean](HBool)
}
