package domain
import scala.util.matching.Regex
import utils._

sealed trait PrimitiveType extends HType
trait HString extends PrimitiveType
trait HInteger extends PrimitiveType
trait HFloat extends PrimitiveType
trait HBool extends PrimitiveType
trait HDate extends PrimitiveType
trait HArray[T <: HType] extends PrimitiveType
trait HFile extends PrimitiveType {
  val sizeInBytes: Int
  val extensions: HArray[HString]
}

sealed trait Literal extends HExpression[HValue[]]
sealed trait SerializableLiteral extends Literal
case class StringLiteral(value: String) extends SerializableLiteral with HString
case class IntegerLiteral(value: Long) extends SerializableLiteral with HInteger
case class FloatLiteral(value: Double) extends SerializableLiteral with HFloat
case class DateLiteral(value: Date) extends SerializableLiteral with HDate
case class BoolLiteral(value: Boolean) extends SerializableLiteral with HBool
case class ArrayLiteral[T <: HType](values: List[T])
    extends SerializableLiteral
    with HArray[T]

sealed trait NonSerializableLiteral extends Literal
case class RegexLiteral(value: Regex) extends NonSerializableLiteral

case class HLazyValue()
case class HValue[T](value: T, dataType: HType)

trait HExpression[Return <: HValue] {
    def eval(): Return
}

// TODO: Object Literal
// Think of a @transform applied to a user field. It needs to return a user object.
// (x.length + 1) + 5
// { ..., name: "5ara" }