package domain
import scala.util.matching.Regex
import domain.utils._
import java.io.File
import scala.util._

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

  sealed trait HValue {
    val htype: HType
  }
  case class HStringValue(value: String) extends HValue {
    final val htype = HString
  }
  case class HIntegerValue(value: Long) extends HValue {
    final val htype = HInteger
  }
  case class HFloatValue(value: Double) extends HValue {
    final val htype = HFloat
  }
  case class HBoolValue(value: Boolean) extends HValue {
    final val htype = HBool
  }
  case class HDateValue(value: Date) extends HValue {
    final val htype = HDate
  }
  case class HArrayValue[T <: HValue](values: List[T], elementType: HType)
      extends HValue {
    final val htype = HArray(elementType)
  }
  case class HFileValue(value: File, htype: HFile) extends HValue
  case class HModelValue(value: HObject, htype: HModel) extends HValue

  trait HExpression {
    def eval(): HValue
  }

  sealed trait Literal extends HExpression {
    val payload: HValue
    override def eval() = payload
  }

  sealed trait SerializableLiteral extends Literal
  case class StringLiteral(payload: HStringValue) extends SerializableLiteral
  case class IntegerLiteral(payload: HIntegerValue) extends SerializableLiteral
  case class FloatLiteral(payload: HFloatValue) extends SerializableLiteral
  case class DateLiteral(payload: HDateValue) extends SerializableLiteral
  case class BoolLiteral(payload: HBoolValue) extends SerializableLiteral
  case class ArrayLiteral[T <: HValue](
      payload: HArrayValue[T]
  ) extends SerializableLiteral

  type HObject = Map[String, HValue]

  sealed trait NonSerializableLiteral extends Literal
  case class RegexLiteral(payload: HStringValue) extends NonSerializableLiteral
}
