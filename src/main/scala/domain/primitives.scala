package domain
import scala.util.matching.Regex
import domain.utils._
import domain._

package object primitives {
  sealed trait PrimitiveType extends HType
  case object HString extends PrimitiveType
  case object HInteger extends PrimitiveType
  case object HFloat extends PrimitiveType
  case object HBool extends PrimitiveType
  case object HDate extends PrimitiveType
  case class HArray(htype: HType) extends PrimitiveType
  case class HFile(sizeInBytes: Int, extensions: List[String])
      extends PrimitiveType

  case class HValue[V](value: V, htype: HType)

  trait HExpression[V] {
    def eval(): HValue[V]
    val htype: HType
  }

  sealed trait Literal[V] extends HExpression[V] {
    val value: V
    val htype: HType
    override def eval() = HValue(value, htype)
  }

  sealed trait SerializableLiteral[V] extends Literal[V]
  case class StringLiteral(value: String, htype: HType = HString)
      extends SerializableLiteral[String]
  case class IntegerLiteral(value: Long, htype: HType = HInteger)
      extends SerializableLiteral[Long]
  case class FloatLiteral(value: Double, htype: HType = HFloat)
      extends SerializableLiteral[Double]
  case class DateLiteral(value: Date, htype: HType = HDate)
      extends SerializableLiteral[Date]
  case class BoolLiteral(value: Boolean, htype: HType = HBool)
      extends SerializableLiteral[Boolean]
  case class ArrayLiteral[T, V <: HExpression[T]](
      value: List[V],
      elementType: HType
  ) extends SerializableLiteral[List[V]] {
    val htype: HType = HArray(this.elementType)
  }

  type HObject = Map[String, HValue[_]]

  sealed trait NonSerializableLiteral[V] extends Literal[V]

  case class RegexLiteral(value: Regex, htype: HType = HString)
      extends NonSerializableLiteral[Regex]
}
