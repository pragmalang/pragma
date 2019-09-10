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
  case class HFunction(args: NamedArgs, returnType: HType) extends PrimitiveType
  case class HOption(htype: HType) extends PrimitiveType

  trait HOperation1 {
    def apply[V <: HValue](arg: V): HValue
  }

  trait HOperation2 {
    def apply[V <: HValue](arg1: V, arg2: V): HValue
  }

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
  case class HFunctionValue(body: HExpression, htype: HFunction) extends HValue
  case class HOptionValue(value: Option[HValue], valueType: HType)
      extends HValue {
    final val htype = HOption(valueType)
  }

  trait HExpression {
    def eval(context: HObject): HValue
  }

  type HObject = Map[String, HValue]

  case class LiteralExpression(value: HValue) extends HExpression {
    override def eval(context: HObject = Map()): HValue = value
  }

  case class RegexLiteral(payload: Regex)

}
