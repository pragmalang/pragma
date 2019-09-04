package domain.primitives
import scala.util.matching.Regex
import domain.utils._
import domain._

package object primitives {
  type HObject[T <: HType] = Map[String, T]

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

  case class HValue[V, HT <: HType](value: V)
  trait HExpression[V, HT <: HType] {
    def eval(): HValue[V, HT]
  }

  sealed trait Literal[V, HT <: HType] extends HExpression[V, HT] {
    val value: V
    override def eval() = new HValue[V, HT](value)
  }

  sealed trait SerializableLiteral[V, HT <: HType] extends Literal[V, HT]
  case class StringLiteral(value: String)
      extends SerializableLiteral[String, HString]
  case class IntegerLiteral(value: Long)
      extends SerializableLiteral[Long, HInteger]
  case class FloatLiteral(value: Double)
      extends SerializableLiteral[Double, HFloat]
  case class DateLiteral(value: Date) extends SerializableLiteral[Date, HDate]
  case class BoolLiteral(value: Boolean)
      extends SerializableLiteral[Boolean, HBool]
  case class ArrayLiteral[T, H <: HType, V <: HExpression[T, H]](value: List[V])
      extends SerializableLiteral[List[V], HArray[H]]
// case class ObjectLiteral(value: Map[String, SerializableLiteral[]])

  sealed trait NonSerializableLiteral[V, HT <: HType] extends Literal[V, HT]

  case class RegexLiteral(value: Regex)
      extends NonSerializableLiteral[Regex, HString]

}
// TODO: Object Literal
// Think of a @transform applied to a user field. It needs to return a user object.
// (x.length + 1) + 5
// { ..., name: "5ara" }
// lexer:tokens -> parser:AST -> optimizer:AST -> validator:Result[AST, Error] -> compiler:GraphQL Server
