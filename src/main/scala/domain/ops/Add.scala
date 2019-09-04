package domain.ops
import domain.primitives._
import domain._

object Add {
  trait Add[A] {
    def add(a: A, b: A): A
  }

  def apply[A](a: A, b: A)(implicit adder: Add[A]): A = adder.add(a, b)
  type HStrVal = HValue[String, HString]
  implicit val hstrAdd: Add[HStrVal] = new Add[HStrVal] {
    def add(a: HStrVal, b: HStrVal) = HValue(a.value + b.value)
  }

  type HIntVal = HValue[Long, HInteger]
  implicit val hintAdd: Add[HIntVal] = new Add[HIntVal] {
    def add(a: HIntVal, b: HIntVal) = HValue(a.value + b.value)
  }

  type HFloatVal = HValue[Double, HFloat]
  implicit val hfloatAdd: Add[HFloatVal] = new Add[HFloatVal] {
    def add(a: HFloatVal, b: HFloatVal) = HValue(a.value + b.value)
  }

  def harrayAddGen[A, B <: HType](): Add[HValue[List[A], HArray[B]]] =
    new Add[HValue[List[A], HArray[B]]] {
      def add(a: HValue[List[A], HArray[B]], b: HValue[List[A], HArray[B]]) =
        HValue(a.value ::: b.value)
    }
  implicit val integerHArrayAdd = harrayAddGen[Int, HInteger]()
  implicit val floatHArrayAdd = harrayAddGen[Double, HFloat]()
  implicit val stringHArrayAdd = harrayAddGen[String, HString]()
  implicit val boolHArrayAdd = harrayAddGen[Boolean, HBool]()
}
