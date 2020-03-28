package domain
import scala.util.matching.Regex
import domain.utils._
import java.io.File
import scala.util._
import scala.collection.immutable.ListMap
import spray.json._
import running.pipeline.Request

package object primitives {

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
  trait PFunctionValue[I, +O] extends Function[I, O] with PValue {
    val ptype: PFunction
    def execute(input: I): O
    override def apply(input: I) = execute(input)
  }
  trait ExternalFunction
      extends PFunctionValue[JsValue, Try[JsValue]]
      with Identifiable {
    override val id: String
    override val ptype: PFunction
    val filePath: String

    def execute(args: JsValue): Try[JsValue]
  }

  trait BuiltinFunction[I, +O]
      extends PFunctionValue[I, Try[O]]
      with Identifiable {
    val ptype: PFunction
    val id: String
    def execute(input: I): Try[O]
  }

  // Takes two predicates of the same input type and
  // returns a predicate that 'ANDs' the result of both input predicates.
  case class PredicateAnd(
      predicateInputType: PType,
      p1: PFunctionValue[JsValue, Try[JsValue]],
      p2: PFunctionValue[JsValue, Try[JsValue]]
  ) extends BuiltinFunction[JsValue, JsBoolean] {
    override val id = "predicateAnd"
    override val ptype = PFunction(ListMap("data" -> predicateInputType), PBool)

    override def execute(input: JsValue): Try[JsBoolean] =
      for {
        p1Result <- p1.execute(input)
        p2Result <- p2.execute(input)
      } yield (p1Result, p2Result) match {
        case (JsBoolean(p), JsBoolean(q)) => JsBoolean(p && q)
        case _ => JsFalse
      }
  }

  case class IfInAuthFunction(id: String, ptype: PFunction)
      extends BuiltinFunction[Request, Request] {
    def execute(input: Request): Try[Request] = ???
  }

  case class IfSelfAuthPredicate(selfModel: PModel)
      extends BuiltinFunction[JsValue, JsBoolean] {
    val id = "ifSelf"

    val ptype = PFunction(
      ListMap(
        "args" -> PInterface(
          "authPredicateArgs",
          List(
            PInterfaceField("user", selfModel, None),
            PInterfaceField("data", selfModel, None)
          ),
          None
        )
      ),
      PBool
    )

    def execute(args: JsValue): Try[JsBoolean] = {
      val ids = args match {
        case JsObject(inputFields) =>
          inputFields
            .get("user")
            .flatMap {
              case JsObject(fields) => fields.get(selfModel.primaryField.id)
              case _                => None
            }
            .zip {
              inputFields.get("data").flatMap {
                case JsObject(fields) => fields.get(selfModel.primaryField.id)
                case _                => None
              }
            }
        case _ => None
      }
      Success(ids match {
        case Some((userId, otherId)) if userId == otherId => JsTrue
        case _                                            => JsFalse
      })
    }
  }

  case class IfRoleAuthPredicate(id: String, ptype: PFunction)
      extends BuiltinFunction[Request, Request] {
    def execute(input: Request): Try[Request] = ???
  }

  case class POptionValue(value: Option[PValue], valueType: PType)
      extends PValue {
    final val ptype = POption(valueType)
  }

  type PObject = ListMap[String, PValue]

  case class RegexLiteral(payload: Regex, position: Option[PositionRange])
      extends Positioned
}
