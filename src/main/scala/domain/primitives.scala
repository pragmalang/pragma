package domain
import scala.util.matching.Regex

sealed trait HType
sealed trait PrimitiveType extends HType

sealed trait Literal
sealed trait SerializableLiteral extends Literal
sealed trait NonSerializableLiteral extends Literal

case class StringLiteral(override val value: String)
    extends HString(value)
    with SerializableLiteral

case class IntegerLiteral(override val value: Long)
    extends HInteger(value)
    with SerializableLiteral

case class FloatLiteral(override val value: Double)
    extends HFloat(value)
    with SerializableLiteral

case class DateLiteral(override val value: String)
    extends HDate(value)
    with SerializableLiteral

case class RegexLitderal(value: Regex) extends NonSerializableLiteral

case class Constant(identifier: String, value: Literal)

class HString(val value: String) extends PrimitiveType
class HInteger(val value: Long) extends PrimitiveType
class HFloat(val value: Double) extends PrimitiveType
class HBool(val value: Boolean) extends PrimitiveType
class HDate(val value: String) extends PrimitiveType
class HArray[T <: HType](val value: List[T]) extends PrimitiveType
class HFile(val size_in_bytes: Int, val extensions: HArray[HString]) extends PrimitiveType

case class Model() extends HType 
