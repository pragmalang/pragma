package domain
import utils._
import domain.primitives._

/**
  * An HType is a data representation (models, enums, and primitive types)
  */
trait HType

sealed trait HConstruct

case class SyntaxTree(constructs: List[HConstruct])

case class HConst[V <: HValue](id: String, value: V)
    extends Identifiable
    with HConstruct

trait HShape[+T <: HShapeField] {
  val fields: List[T]
}

case class HModel(
    id: String,
    fields: List[HModelField],
    directives: List[ModelDirective]
) extends HType
    with Identifiable
    with HShape[HModelField]
    with HConstruct {
  lazy val isUser = directives.exists(d => d.id == "user")
  lazy val isExposed = directives.exists(d => d.id == "expose")
}

case class HInterface(
    id: String,
    fields: List[HInterfaceField]
) extends HType
    with Identifiable
    with HShape[HInterfaceField]

trait HShapeField
case class HModelField(
    id: String,
    htype: HType,
    defaultValue: Option[HValue],
    directives: List[FieldDirective],
    isOptional: Boolean
) extends Identifiable
    with HShapeField

case class HInterfaceField(
    id: String,
    htype: HType,
    isOptional: Boolean
) extends Identifiable
    with HShapeField

sealed trait Directive extends Identifiable {
  val id: String
  val args: HInterfaceValue
}
object Directive {
  def modelDirectives(self: HModel) = Map(
    "validate" -> HInterface(
      "validate",
      List(HInterfaceField("validator", self, false))
    ),
    "user" -> HInterface("user", Nil)
  )

  def fieldDirectives(model: HModel, field: HModelField) = Map(
    "set" -> HInterface(
      "set",
      HInterfaceField("self", model, false) ::
        HInterfaceField("new", field.htype, false) :: Nil
    ),
    "get" -> HInterface(
      "get",
      HInterfaceField("self", model, false) :: Nil
    ),
    "id" -> HInterface("id", Nil),
    "unique" -> HInterface("unique", Nil)
  )
}

case class ModelDirective(id: String, args: HInterfaceValue) extends Directive

case class FieldDirective(id: String, args: HInterfaceValue) extends Directive

case class ServiceDirective(id: String, args: HInterfaceValue) extends Directive

case class HEnum(id: String, values: List[String]) extends Identifiable

sealed trait HEvent
case object Read extends HEvent
case object Create extends HEvent
case object Update extends HEvent
case object Delete extends HEvent
case object All extends HEvent
