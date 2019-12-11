package domain

import utils._, primitives._
import org.parboiled2.Position

import org.graalvm.polyglot
import spray.json._
import spray.json.{JsValue, JsObject}
import scala.util.Try
import domain.Implicits.GraalValueJsonFormater
import running.pipeline.{PipelineInput, PipelineOutput}
import parsing.HeavenlyParser

/**
  * An HType is a data representation (models, enums, and primitive types)
  */
trait HType

trait HReferenceType extends HType with Identifiable

// Base case for recursive types
case class HSelf(id: String) extends HReferenceType

case class HReference(id: String) extends HReferenceType

sealed trait HConstruct extends Positioned

case class SyntaxTree(
    imports: List[HImport],
    models: List[HModel],
    enums: List[HEnum],
    permissions: Option[Permissions] = None,
    config: Option[HConfig] = None
) {
  def findTypeById(id: String): Option[HType] =
    models.find(model => model.id == id) orElse
      enums.find(enum => enum.id == id)
}
object SyntaxTree {
  def from(code: String): Try[SyntaxTree] =
    Try(fromConstructs(new HeavenlyParser(code).syntaxTree.run().get))

  def fromConstructs(constructs: List[HConstruct]): SyntaxTree = {
    val imports = constructs.collect { case i: HImport   => i }
    val models = constructs.collect { case m: HModel     => m }
    val enums = constructs.collect { case e: HEnum       => e }
    val acl = constructs.collect { case acl: Permissions => acl }
    val config = constructs.collect { case cfg: HConfig  => cfg }
    SyntaxTree(
      imports,
      models,
      enums,
      if (acl.isEmpty) None else Some(acl.head),
      if (config.isEmpty) None else Some(config.head)
    )
  }
}

case class PositionRange(start: Position, end: Position)

trait Positioned {
  val position: Option[PositionRange]
}

case class HImport(
    id: String,
    filePath: String,
    position: Option[PositionRange]
) extends HConstruct
    with Identifiable
    with Positioned

trait HShape extends Identifiable {
  override val id: String
  val fields: List[HShapeField]
}

case class HModel(
    id: String,
    fields: List[HModelField],
    directives: List[ModelDirective],
    position: Option[PositionRange]
) extends HType
    with HConstruct
    with HShape {
  lazy val isUser = directives.exists(_.id == "user")

  lazy val primaryField =
    fields.find(_.directives.exists(_.id == "primary")).get

  lazy val hooks = directives.filter(_.id == "validate")
}

case class HInterface(
    id: String,
    fields: List[HInterfaceField],
    position: Option[PositionRange]
) extends HType
    with HShape //with HConstruct

trait HShapeField extends Positioned with Identifiable {
  val htype: HType
  def isOptional = htype match {
    case HOption(_) => true
    case _          => false
  }
}

case class HModelField(
    id: String,
    htype: HType,
    defaultValue: Option[HValue],
    directives: List[FieldDirective],
    position: Option[PositionRange]
) extends HShapeField {
  lazy val hooks = directives.filter(d => d.id == "get" || d.id == "set")
}

case class HInterfaceField(
    id: String,
    htype: HType,
    position: Option[PositionRange]
) extends HShapeField

sealed trait Directive extends Identifiable {
  val id: String
  val args: HInterfaceValue
}
object BuiltInDefs {
  def modelDirectives(self: HModel) = Map(
    "validate" -> HInterface(
      "validate",
      List(HInterfaceField("validator", self, None)),
      None
    ),
    "user" -> HInterface("user", Nil, None),
    "plural" -> HInterface(
      "plural",
      List(HInterfaceField("name", HString, None)),
      None
    )
  )

  def fieldDirectives(model: HModel, field: HModelField) = Map(
    "set" -> HInterface(
      "set",
      HInterfaceField("self", model, None) ::
        HInterfaceField("new", field.htype, None) :: Nil,
      None
    ),
    "get" -> HInterface(
      "get",
      HInterfaceField("self", model, None) :: Nil,
      None
    ),
    "id" -> HInterface("id", Nil, None),
    "unique" -> HInterface("unique", Nil, None)
  )

  // e.g. ifSelf & ifOwner
  val builtinFunctions = Map.empty[String, BuiltinFunction]
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
    with HType
    with HConstruct

sealed trait HEvent
case object Read extends HEvent
case object Create extends HEvent
case object Update extends HEvent
case object Delete extends HEvent
case object All extends HEvent

case class Permissions(
    globalTenant: Tenant,
    tenents: List[Tenant],
    position: Option[PositionRange]
) extends HConstruct

case class Tenant(
    id: String,
    rules: List[AccessRule],
    roles: List[Role],
    position: Option[PositionRange]
) extends Identifiable
    with Positioned

case class Role(user: HType, rules: List[AccessRule])

sealed trait RuleKind
case object Allow extends RuleKind
case object Deny extends RuleKind

case class AccessRule(
    ruleKind: RuleKind,
    resource: Resource,
    actions: List[HEvent],
    predicate: HFunctionValue[PipelineInput, PipelineOutput],
    position: Option[PositionRange]
)

trait Resource {
  val shape: HShape
}

case class ShapeResource(shape: HShape) extends Resource
case class FieldResource(field: HShapeField, shape: HShape)

case class HConfig(values: List[ConfigEntry], position: Option[PositionRange])
    extends HConstruct

case class ConfigEntry(
    key: String,
    value: HValue,
    position: Option[PositionRange]
) extends Positioned

case class DockerFunction(id: String, filePath: String, htype: HFunction)
    extends ExternalFunction {
  override def execute(input: JsValue): Try[JsValue] = ???
}

case class GraalFunction(
    id: String,
    htype: HFunction,
    filePath: String,
    graalCtx: polyglot.Context,
    languageId: String = "js"
) extends ExternalFunction {
  val graalFunction = graalCtx.getBindings(languageId).getMember(id)

  override def execute(input: JsValue): Try[JsValue] =
    Try(
      GraalValueJsonFormater
        .write(graalFunction.execute(graalCtx.eval(languageId, s"($input)")))
    )
}