package domain
import scala.util.matching.Regex
import domain.utils._
import java.io.File
import scala.util._
import scala.collection.immutable.ListMap

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

  trait HOperation {
    val arity: Int
    def apply(args: List[HValue]): HValue
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
  case class HInterfaceValue(value: HObject, htype: HInterface) extends HValue
  case class HFunctionValue(body: HExpression, htype: HFunction) extends HValue
  case class HOptionValue(value: Option[HValue], valueType: HType)
      extends HValue {
    final val htype = HOption(valueType)
  }

  trait HExpression {
    protected var cache: Option[(HObject, HValue)] = None
    def eval(context: HObject): HValue
    def checkCache(context: HObject)(cb: () => HValue): HValue = cache match {
      case Some((prevContext, prevValue)) if prevContext == context =>
        prevValue
      case _ => cb()
    }
  }

  type HObject = ListMap[String, HValue]

  case class LiteralExpression(value: HValue) extends HExpression {
    override def eval(context: HObject = ListMap()): HValue = value
  }

  case class RegexLiteral(payload: Regex)

  case class Permissions(tenents: List[Tenant])

  case class Tenant(id: String, rules: List[AccessRule]) extends Identifiable

  sealed trait AccessRule {
    val resource: Resource
    val actions: List[HEvent]
    val predicate: HFunctionValue
  }

  case class RoleBasedRule(
      user: HModel,
      resource: Resource,
      actions: List[HEvent],
      predicate: HFunctionValue
  ) extends AccessRule

  case class GlobalRule(
      resource: Resource,
      actions: List[HEvent],
      predicate: HFunctionValue
  ) extends AccessRule

  case class Resource(field: HShapeField, shape: HShape[HShapeField])

}
