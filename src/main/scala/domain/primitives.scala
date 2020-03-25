package domain
import scala.util.matching.Regex
import domain.utils._
import java.io.File
import scala.util._
import scala.collection.immutable.ListMap
import spray.json.JsValue

package object primitives {

import running.pipeline.Request

  sealed trait PrimitiveType extends PType
  case object PString extends PrimitiveType
  case object PInt extends PrimitiveType
  case object PFloat extends PrimitiveType
  case object PBool extends PrimitiveType
  case object PDate extends PrimitiveType
  case class PArray(ptype: PType) extends PrimitiveType
  case class HFile(sizeInBytes: Int, extensions: List[String])
      extends PrimitiveType
  case class PFunction(args: NamedArgs, returnType: PType) extends PrimitiveType
  case class POption(ptype: PType) extends PrimitiveType

  trait HOperation {
    val arity: Int
    def apply(args: List[PValue]): PValue
  }

  sealed trait PValue {
    val ptype: PType
  }
  case class PStringValue(value: String) extends PValue {
    final val ptype = PString
  }
  case class PIntValue(value: Long) extends PValue {
    final val ptype = PInt
  }
  case class PFloatValue(value: Double) extends PValue {
    final val ptype = PFloat
  }
  case class PBoolValue(value: Boolean) extends PValue {
    final val ptype = PBool
  }
  case class PDateValue(value: Date) extends PValue {
    final val ptype = PDate
  }
  case class PArrayValue(values: List[PValue], elementType: PType)
      extends PValue {
    final val ptype = PArray(elementType)
  }
  case class PFileValue(value: File, ptype: HFile) extends PValue
  case class PModelValue(value: PObject, ptype: PModel) extends PValue
  case class PInterfaceValue(value: PObject, ptype: PInterface) extends PValue
  trait PFunctionValue[I, O] extends PValue {
    val ptype: PFunction
    def execute(input: I): O
  }
  trait ExternalFunction
      extends PFunctionValue[JsValue, Try[JsValue]]
      with Identifiable {
    override val id: String
    override val ptype: PFunction
    val filePath: String

    def execute(args: JsValue): Try[JsValue]
  }

  trait BuiltinFunction[I, O]
      extends PFunctionValue[I, Try[O]]
      with Identifiable {
    val ptype: PFunction
    val id: String
    def execute(input: I): Try[O]
  }

  case class IfInAuthFunction(id: String, ptype: PFunction)
      extends BuiltinFunction[Request, Request] {
    def execute(input: Request): Try[Request] = ???
  }

  case class IfSelfAuthFunction(id: String, ptype: PFunction)
      extends BuiltinFunction[Request, Request] {
    def execute(input: Request): Try[Request] = ???
  }

  case class IfRoleAuthFunction(id: String, ptype: PFunction)
      extends BuiltinFunction[Request, Request] {
    def execute(input: Request): Try[Request] = ???
  }

  case class POptionValue(value: Option[PValue], valueType: PType)
      extends PValue {
    final val ptype = POption(valueType)
  }

  trait PExpression extends Positioned {
    protected var cache: Option[(PObject, PValue)] = None
    def eval(context: PObject): PValue
    def checkCache(context: PObject)(cb: () => PValue): PValue = cache match {
      case Some((prevContext, prevValue)) if prevContext == context =>
        prevValue
      case _ => cb()
    }
  }

  type PObject = ListMap[String, PValue]

  case class LiteralExpression(value: PValue, position: Option[PositionRange])
      extends PExpression {
    override def eval(context: PObject = ListMap()): PValue = value
  }

  case class RegexLiteral(payload: Regex, position: Option[PositionRange])
      extends Positioned
}
