package domain

import utils._, primitives._
import org.parboiled2.Position

import org.graalvm.polyglot
import spray.json._
import scala.util.Try
import scala.collection.immutable.ListMap
import domain.Implicits.GraalValueJsonFormater
import parsing.{HeavenlyParser, Validator, Substitutor}
import running.pipeline.Request

/**
  * An HType is a data representation (models, enums, and primitive types)
  */
trait HType

case object HAny extends HType

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
    models.find(model => model.id.toLowerCase == id.toLowerCase) orElse
      enums.find(enum => enum.id == id)

  def render: String =
    (models ++ enums).map(displayHType(_, true)).mkString("\n\n")
}
object SyntaxTree {
  // The resulting syntax tree is validated and substituted
  def from(code: String): Try[SyntaxTree] =
    new HeavenlyParser(code).syntaxTree
      .run()
      .flatMap(new Validator(_).validSyntaxTree)
      .flatMap(Substitutor.substitute)

  // The resulting syntax tree is not validated or substituted
  // Meant for use only in the HeavenlyParser
  def fromConstructs(constructs: List[HConstruct]): SyntaxTree = {
    val imports = constructs.collect { case i: HImport         => i }
    val models = constructs.collect { case m: HModel           => m }
    val enums = constructs.collect { case e: HEnum             => e }
    val config = constructs.collect { case cfg: HConfig        => cfg }
    val accessRules = constructs.collect { case ar: AccessRule => ar }
    val roles = constructs.collect { case r: Role              => r }
    lazy val permissions = Permissions(
      Tenant("root", accessRules, roles, None),
      Nil // TODO: Add support for user-defined tenants
    )
    SyntaxTree(
      imports,
      models,
      enums,
      if (accessRules.isEmpty && roles.isEmpty) None else Some(permissions),
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
    directives: List[Directive],
    position: Option[PositionRange]
) extends HType
    with HConstruct
    with HShape {
  lazy val isUser = directives.exists(_.id == "user")

  lazy val primaryField =
    fields.find(_.directives.exists(_.id == "primary")).get

  lazy val readHooks = directives
    .filter(_.id == "onRead")
    .map { dir =>
      dir.args.value.get("function") match {
        case Some(fn: HFunctionValue[_, _]) => fn
        case None =>
          throw new InternalException(
            s"`onRead` directive of model `$id` must have one function argument. Something must've went wrong during validation"
          )
        case _ =>
          throw new InternalException(
            s"Function provided to `onRead` of model `$id` should be a function. Something must've went wrong during substitution"
          )
      }
    }

  lazy val writeHooks = directives
    .filter(_.id == "onWrite")
    .map { dir =>
      dir.args.value.get("function") match {
        case Some(fn: HFunctionValue[_, _]) => fn
        case None =>
          throw new InternalException(
            s"`onWrite` directive of model `$id` must have one function argument. Something must've went wrong during validation"
          )
        case _ =>
          throw new InternalException(
            s"Value provided to `onWrite` of model `$id` should be a function. Something must've went wrong during substitution"
          )
      }
    }

  lazy val deleteHooks = directives
    .filter(_.id == "onDelete")
    .map { dir =>
      dir.args.value.get("function") match {
        case Some(fn: HFunctionValue[_, _]) => fn
        case None =>
          throw new InternalException(
            s"`onDelete` directive of model `$id` must have one function argument. Something must've went wrong during validation"
          )
        case _ =>
          throw new InternalException(
            s"Value provided to `onDelete` of model `$id` should be a function. Something must've went wrong during substitution"
          )
      }
    }
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
    directives: List[Directive],
    position: Option[PositionRange]
) extends HShapeField

case class HInterfaceField(
    id: String,
    htype: HType,
    position: Option[PositionRange]
) extends HShapeField

case class Directive(
    id: String,
    args: HInterfaceValue,
    kind: DirectiveKind,
    position: Option[PositionRange] = None
) extends Identifiable
    with Positioned

sealed trait DirectiveKind
case object FieldDirective extends DirectiveKind
case object ModelDirective extends DirectiveKind
case object ServiceDirective extends DirectiveKind

object BuiltInDefs {
  def modelDirectives(self: HModel) = Map(
    "user" -> HInterface("user", Nil, None),
    "onWrite" -> HInterface(
      "onWrite",
      HInterfaceField(
        "function",
        HFunction(ListMap("self" -> self, "request" -> Request.hType), HAny),
        None
      ) :: Nil,
      None
    ),
    "onRead" -> HInterface(
      "onRead",
      HInterfaceField(
        "function",
        HFunction(ListMap("request" -> Request.hType), HAny),
        None
      ) :: Nil,
      None
    ),
    "onDelete" -> HInterface(
      "onDelete",
      HInterfaceField(
        "function",
        HFunction(ListMap("request" -> Request.hType), HAny),
        None
      ) :: Nil,
      None
    ),
    "noStorage" -> HInterface("noStorage", Nil, None)
  )

  def fieldDirectives(model: HModel, field: HModelField) = Map(
    "uuid" -> HInterface("uuid", Nil, None),
    "autoIncrement" -> HInterface("autoIncrement", Nil, None),
    "unique" -> HInterface("unique", Nil, None),
    "primary" -> HInterface("primary", Nil, None),
    "id" -> HInterface("id", Nil, None), // auto-increment/UUID & unique
    "publicCredential" -> HInterface("publicCredential", Nil, None),
    "secretCredential" -> HInterface("secretCredential", Nil, None),
    "connect" -> HInterface(
      "connect",
      List(HInterfaceField("to", HString, None)),
      None
    ),
    "recoverable" -> HInterface("recoverable", Nil, None)
  )

  // e.g. ifSelf & ifOwner
  val builtinFunctions =
    Map.empty[String, BuiltinFunction[JsValue, JsValue]]
}

case class HEnum(
    id: String,
    values: List[String],
    position: Option[PositionRange]
) extends Identifiable
    with HType
    with HConstruct

sealed trait HEvent {
  override def toString(): String = this match {
    case All         => "ALL"
    case Create      => "CREATE"
    case Delete      => "DELETE"
    case Mutate      => "MUTATE"
    case PushTo      => "PUSH_TO"
    case Read        => "READ"
    case ReadMany    => "LIST"
    case Recover     => "RECOVER"
    case RemoveFrom  => "REMOVE_FROM"
    case SetOnCreate => "SET_ON_CREATE"
    case Update      => "UPDATE"
    case e           => e.toString
  }
}
case object Read extends HEvent // Retrieve record by IDe
case object Create extends HEvent
case object Update extends HEvent
case object Delete extends HEvent
case object All extends HEvent // Includes all the above
case object ReadMany extends HEvent // Retrieve many records. Translates to LIST event
case object Mutate extends HEvent
case object PushTo extends HEvent // Add item to array field
case object RemoveFrom extends HEvent // Remove item from array field
// Permission to send attribute in create request
// e.g. If aa `User` model has a `verified` attribute that you don't want the user to set
// when they create their aaccount.
case object SetOnCreate extends HEvent
case object Recover extends HEvent // Undelete a record

case class Permissions(
    globalTenant: Tenant,
    tenants: List[Tenant]
)

case class Tenant(
    id: String,
    rules: List[AccessRule],
    roles: List[Role],
    position: Option[PositionRange]
) extends Identifiable
    with Positioned

case class Role(
    user: HReference,
    rules: List[AccessRule],
    position: Option[PositionRange] = None
) extends HConstruct

sealed trait RuleKind
case object Allow extends RuleKind
case object Deny extends RuleKind

case class AccessRule(
    ruleKind: RuleKind,
    resourcePath: (HShape, Option[HShapeField]),
    actions: List[HEvent],
    predicate: Option[HFunctionValue[JsValue, Try[JsValue]]],
    position: Option[PositionRange]
) extends HConstruct

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

  override def execute(input: JsValue): Try[JsValue] = Try {
    GraalValueJsonFormater
      .write(graalFunction.execute(graalCtx.eval(languageId, s"($input)")))
  }
}
