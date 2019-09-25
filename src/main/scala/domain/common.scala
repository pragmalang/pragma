package domain
import utils._, primitives._
import org.parboiled2.Position

/**
  * An HType is a data representation (models, enums, and primitive types)
  */
trait HType

sealed trait HConstruct extends Positioned

case class SyntaxTree(constructs: List[HConstruct])
case class PositionRange(start: Position, end: Position)

trait Positioned {
  val position: Option[PositionRange]
}

case class HConst(id: String, value: HValue, position: Option[PositionRange])
    extends Identifiable
    with HConstruct

trait HShape extends Identifiable with HConstruct {
  override val id: String
  val fields: List[HShapeField]
}

case class HModel(
    id: String,
    fields: List[HModelField],
    directives: List[ModelDirective],
    position: Option[PositionRange]
) extends HType
    with HShape {
  lazy val isUser = directives.exists(d => d.id == "user")
}

case class HInterface(
    id: String,
    fields: List[HInterfaceField],
    position: Option[PositionRange]
) extends HType
    with HShape

trait HShapeField extends Positioned
case class HModelField(
    id: String,
    htype: HType,
    defaultValue: Option[HValue],
    directives: List[FieldDirective],
    isOptional: Boolean,
    position: Option[PositionRange]
) extends Identifiable
    with HShapeField

case class HInterfaceField(
    id: String,
    htype: HType,
    isOptional: Boolean,
    position: Option[PositionRange]
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
      List(HInterfaceField("validator", self, false, None)),
      None
    ),
    "user" -> HInterface("user", Nil, None)
  )

  def fieldDirectives(model: HModel, field: HModelField) = Map(
    "set" -> HInterface(
      "set",
      HInterfaceField("self", model, false, None) ::
        HInterfaceField("new", field.htype, false, None) :: Nil,
      None
    ),
    "get" -> HInterface(
      "get",
      HInterfaceField("self", model, false, None) :: Nil,
      None
    ),
    "id" -> HInterface("id", Nil, None),
    "unique" -> HInterface("unique", Nil, None)
  )
}

case class ModelDirective(
    id: String,
    args: HInterfaceValue,
    position: Option[PositionRange]
) extends Directive

case class FieldDirective(
    id: String,
    args: HInterfaceValue,
    position: Option[PositionRange]
) extends Directive

case class ServiceDirective(
    id: String,
    args: HInterfaceValue,
    position: Option[PositionRange]
) extends Directive

case class HEnum(
    id: String,
    values: List[String],
    position: Option[PositionRange]
) extends Identifiable

sealed trait HEvent
case object Read extends HEvent
case object Create extends HEvent
case object Update extends HEvent
case object Delete extends HEvent
case object All extends HEvent

case class Permissions(tenents: List[Tenant])

case class Tenant(
    id: String,
    rules: List[AccessRule],
    position: Option[PositionRange]
) extends Identifiable

sealed trait AccessRule {
  val resource: Resource
  val actions: List[HEvent]
  val predicate: HFunctionValue
  val position: Option[PositionRange]
}

case class RoleBasedRule(
    user: HModel,
    resource: Resource,
    actions: List[HEvent],
    predicate: HFunctionValue,
    position: Option[PositionRange]
) extends AccessRule

case class GlobalRule(
    resource: Resource,
    actions: List[HEvent],
    predicate: HFunctionValue,
    position: Option[PositionRange]
) extends AccessRule

trait Resource {
  val shape: HShape
}

case class ShapeResource(shape: HShape) extends Resource
case class FieldResource(field: HShapeField, shape: HShape)
