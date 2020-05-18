package domain

import domain.utils._
import domain.Implicits._
import scala.util.matching.Regex
import java.io.File
import scala.util._
import spray.json._
import running.pipeline.Request
import org.graalvm.polyglot

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

case class PFileValue(value: File, ptype: PFile) extends PValue

case class PModelValue(value: Map[String, PValue], ptype: PModel) extends PValue

case class PInterfaceValue(value: Map[String, PValue], ptype: PInterface)
    extends PValue

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

/** Takes two predicates of the same input type and
  *  returns a predicate that 'ANDs' the result of both input predicates.
  */
case class PredicateAnd(
    predicateInputType: PType,
    p1: PFunctionValue[JsValue, Try[JsValue]],
    p2: PFunctionValue[JsValue, Try[JsValue]]
) extends BuiltinFunction[JsValue, JsBoolean] {
  override val id = "predicateAnd"
  override val ptype = PFunction(Map("data" -> predicateInputType), PBool)

  override def execute(input: JsValue): Try[JsBoolean] =
    for {
      p1Result <- p1.execute(input)
      p2Result <- p2.execute(input)
    } yield
      (p1Result, p2Result) match {
        case (JsBoolean(p), JsBoolean(q)) => JsBoolean(p && q)
        case _                            => JsFalse
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
    Map(
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

case class RegexLiteral(payload: Regex, position: Option[PositionRange])
    extends Positioned

case class DockerFunction(id: String, filePath: String, ptype: PFunction)
    extends ExternalFunction {
  override def execute(input: JsValue): Try[JsValue] = ???
}

case class GraalFunction(
    id: String,
    ptype: PFunction,
    filePath: String,
    graalCtx: polyglot.Context,
    languageId: String = "js"
) extends ExternalFunction {
  val graalFunction = graalCtx.getBindings(languageId).getMember(id)

  override def execute(input: JsValue): Try[JsValue] = Try {
    GraalValueJsonFormater
      .write(graalFunction.execute(graalCtx.eval(languageId, s"($input)")))
  }
}