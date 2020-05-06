package domain

import utils._, primitives._
import org.parboiled2.Position

import org.graalvm.polyglot
import spray.json._
import scala.util.Try
import scala.collection.immutable.ListMap
import domain.Implicits.GraalValueJsonFormater
import parsing.{PragmaParser, Validator, Substitutor}
import running.pipeline.Request

/**
  * A PType is a data representation (models, enums, and primitive types)
  */
trait PType

case object PAny extends PType

trait PReferenceType extends PType with Identifiable

case class PReference(id: String) extends PReferenceType

sealed trait PConstruct extends Positioned

case class SyntaxTree(
    imports: List[PImport],
    models: List[PModel],
    enums: List[PEnum],
    permissions: Permissions,
    config: Option[PConfig] = None
) {
  def findTypeById(id: String): Option[PType] =
    models.find(model => model.id.toLowerCase == id.toLowerCase) orElse
      enums.find(enum => enum.id == id)

  def render: String =
    (models ++ enums).map(displayPType(_, true)).mkString("\n\n")

  def getConfigEntry(key: String): Option[ConfigEntry] =
    config.flatMap(_.getConfigEntry(key))

}
object SyntaxTree {
  // The resulting syntax tree is validated and substituted
  def from(code: String): Try[SyntaxTree] =
    new PragmaParser(code).syntaxTree
      .run()
      .flatMap(new Validator(_).validSyntaxTree)
      .flatMap(Substitutor.substitute)

  /**
    * The resulting syntax tree is not validated or substituted
    * Meant for use only in the PragmaParser
    */
  def fromConstructs(constructs: List[PConstruct]): SyntaxTree = {
    val imports = constructs.collect { case i: PImport         => i }
    val models = constructs.collect { case m: PModel           => m }
    val enums = constructs.collect { case e: PEnum             => e }
    val config = constructs.collect { case cfg: PConfig        => cfg }
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
      permissions,
      if (config.isEmpty) None else Some(config.head)
    )
  }
}

case class PositionRange(start: Position, end: Position)

trait Positioned {
  val position: Option[PositionRange]
}

case class PImport(
    id: String,
    filePath: String,
    position: Option[PositionRange]
) extends PConstruct
    with Identifiable
    with Positioned

trait PShape extends PType with Identifiable {
  override val id: String
  val fields: List[PShapeField]
}

case class PModel(
    id: String,
    fields: List[PModelField],
    directives: List[Directive],
    position: Option[PositionRange]
) extends PType
    with PConstruct
    with PShape {
  lazy val isUser = directives.exists(_.id == "user")

  lazy val primaryField =
    fields.find(_.directives.exists(_.id == "primary")).get

  lazy val readHooks = directives
    .filter(_.id == "onRead")
    .map { dir =>
      dir.args.value.get("function") match {
        case Some(fn: PFunctionValue[_, _]) => fn
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
        case Some(fn: PFunctionValue[_, _]) => fn
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
        case Some(fn: PFunctionValue[_, _]) => fn
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

case class PInterface(
    id: String,
    fields: List[PInterfaceField],
    position: Option[PositionRange]
) extends PType
    with PShape //with HConstruct

trait PShapeField extends Positioned with Identifiable {
  val ptype: PType
  def isOptional = ptype match {
    case POption(_) => true
    case _          => false
  }
}

case class PModelField(
    id: String,
    ptype: PType,
    defaultValue: Option[PValue],
    directives: List[Directive],
    position: Option[PositionRange]
) extends PShapeField

case class PInterfaceField(
    id: String,
    ptype: PType,
    position: Option[PositionRange]
) extends PShapeField

case class Directive(
    id: String,
    args: PInterfaceValue,
    kind: DirectiveKind,
    position: Option[PositionRange] = None
) extends Identifiable
    with Positioned

sealed trait DirectiveKind
case object FieldDirective extends DirectiveKind
case object ModelDirective extends DirectiveKind
case object ServiceDirective extends DirectiveKind

object BuiltInDefs {
  def modelDirectives(self: PModel) = Map(
    "user" -> PInterface("user", Nil, None),
    "onWrite" -> PInterface(
      "onWrite",
      PInterfaceField(
        "function",
        PFunction(ListMap("self" -> self, "request" -> Request.pType), PAny),
        None
      ) :: Nil,
      None
    ),
    "onRead" -> PInterface(
      "onRead",
      PInterfaceField(
        "function",
        PFunction(ListMap("request" -> Request.pType), PAny),
        None
      ) :: Nil,
      None
    ),
    "onDelete" -> PInterface(
      "onDelete",
      PInterfaceField(
        "function",
        PFunction(ListMap("request" -> Request.pType), PAny),
        None
      ) :: Nil,
      None
    ),
    "noStorage" -> PInterface("noStorage", Nil, None)
  )

  def fieldDirectives(model: PModel, field: PModelField) = Map(
    "uuid" -> PInterface("uuid", Nil, None),
    "autoIncrement" -> PInterface("autoIncrement", Nil, None),
    "unique" -> PInterface("unique", Nil, None),
    "primary" -> PInterface("primary", Nil, None),
    "id" -> PInterface("id", Nil, None), // auto-increment/UUID & unique
    "publicCredential" -> PInterface("publicCredential", Nil, None),
    "secretCredential" -> PInterface("secretCredential", Nil, None),
    "connection" -> PInterface(
      "connection",
      List(PInterfaceField("name", PString, None)),
      None
    )
  )

  // e.g. ifSelf & ifOwner
  val builtinFunctions =
    Map.empty[String, BuiltinFunction[JsValue, JsValue]]
}

case class PEnum(
    id: String,
    values: List[String],
    position: Option[PositionRange]
) extends Identifiable
    with PType
    with PConstruct

sealed trait PEvent {
  override def toString(): String = this match {
    case Create            => "CREATE"
    case Delete            => "DELETE"
    case PushTo(_)         => "PUSH_TO"
    case PushManyTo(_)     => "PUSH_MANY_TO"
    case Read              => "READ"
    case ReadMany          => "LIST"
    case RemoveFrom(_)     => "REMOVE_FROM"
    case RemoveManyFrom(_) => "REMOVE_MANY_FROM"
    case Update            => "UPDATE"
    case CreateMany        => "CREATE_MANY"
    case DeleteMany        => "DELETE_MANY"
    case Login             => "LOGIN"
    case UpdateMany        => "UPDATE_MANY"
  }

  def render(model: PModel): String = {
    val transformedFieldId = (f: PShapeField) =>
      if (model.fields
            .filter(_.id.toLowerCase == f.id.toLowerCase)
            .length > 1)
        f.id
      else
        f.id.capitalize
    this match {
      case Read              => "read"
      case ReadMany          => "list"
      case Create            => "create"
      case CreateMany        => "createMany"
      case Update            => "update"
      case UpdateMany        => "updateMany"
      case Delete            => "delete"
      case DeleteMany        => "deleteMany"
      case PushTo(listField) => s"pushTo${transformedFieldId(listField)}"
      case PushManyTo(listField) =>
        s"pushManyTo${transformedFieldId(listField)}"
      case RemoveFrom(listField) =>
        s"removeFrom${transformedFieldId(listField)}"
      case RemoveManyFrom(listField) =>
        s"removeManyFrom${transformedFieldId(listField)}"
      case Login => "login"
    }
  }
}

sealed trait ReadEvent extends PEvent
sealed trait CreateEvent extends PEvent
sealed trait UpdateEvent extends PEvent
sealed trait DeleteEvent extends PEvent

sealed trait PPermission {
  override def toString(): String = this match {
    case All         => "ALL"
    case SetOnCreate => "SET_ON_CREATE"
    case Mutate      => "MUTATE"
    case PushTo      => "PUSH_TO"
    case RemoveFrom  => "REMOVE_FROM"
    case e: PEvent   => e.toString
  }
}
object PPermission {
  lazy val allowedArrayFieldPermissions: List[PPermission] =
    List(Read, Update, SetOnCreate, PushTo, RemoveFrom, Mutate)
  lazy val allowedPrimitiveFieldPermissions: List[PPermission] =
    List(Read, Update, SetOnCreate)
  lazy val allowedModelPermissions: List[PPermission] =
    List(Read, Update, Create, Delete)
  lazy val allowedModelFieldPermissions: List[PPermission] =
    List(Read, Update, Mutate, SetOnCreate)
}

/** Retrieve record by ID */
case object Read extends PPermission with ReadEvent

/** Translates to LIST event */
case object ReadMany extends ReadEvent
case object Create extends PPermission with CreateEvent
case object CreateMany extends CreateEvent
case object Update extends PPermission with UpdateEvent
case object UpdateMany extends UpdateEvent
case object Delete extends PPermission with DeleteEvent
case object DeleteMany extends DeleteEvent
case object All extends PPermission
case object Mutate extends PPermission

case class PushTo(listField: PShapeField) extends UpdateEvent
case object PushTo extends PPermission

case class RemoveFrom(listField: PShapeField) extends UpdateEvent
case object RemoveFrom extends PPermission

case class PushManyTo(listField: PShapeField) extends UpdateEvent

case class RemoveManyFrom(listField: PShapeField) extends UpdateEvent

/**
  * Permission to send attribute in create request
  * e.g. If aa `User` model has a `verified` attribute that you don't want the user to set
  * when they create their aaccount.
  */
case object SetOnCreate extends PPermission // allowed by default
case object Login extends PPermission with PEvent // allowed by default

case class Permissions(
    globalTenant: Tenant,
    tenants: List[Tenant]
) {
  type TargetModelId = String
  type RoleId = String
  type PermissionTree =
    Map[Option[RoleId], Map[TargetModelId, Map[PEvent, List[AccessRule]]]]

  /**
    * A queryable tree of permissions.
    * Note: it's better to use the methods on `Permissions`
    * for querying instead of directly accessing `tree`.
    */
  lazy val tree: PermissionTree = constructTree

  private def ruleEventTree(
      rules: List[AccessRule]
  ): Map[PEvent, List[AccessRule]] = {
    val eventRulePairs =
      rules.flatMap(rule => rule.eventsThatMatch.map((_, rule)))
    eventRulePairs.groupMap(_._1)(_._2)
  }

  private def targetModelTree(
      rules: List[AccessRule]
  ): Map[TargetModelId, Map[PEvent, List[AccessRule]]] =
    rules.groupBy(_.resourcePath._1.id).map {
      case (targetModelId, rules) => (targetModelId, ruleEventTree(rules))
    }

  private def constructTree: PermissionTree = {
    val trees =
      globalTenant.roles.map { role =>
        Option(role.user.id) -> targetModelTree(
          globalTenant.rules ::: role.rules
        )
      }
    trees.toMap.withDefaultValue(targetModelTree(globalTenant.rules))
  }

  def rulesOf(
      role: Option[RoleId],
      targetModel: TargetModelId,
      event: PEvent
  ): List[AccessRule] = {
    val targetModelTree = tree(role)
    val rules = for {
      eventTree <- targetModelTree.get(targetModel)
      rules <- eventTree.get(event)
    } yield rules

    rules.getOrElse(Nil)
  }

  def rulesOf(
      role: Option[PModel],
      targetModel: PModel,
      event: PEvent
  ): List[AccessRule] =
    rulesOf(role.map(_.id), targetModel.id, event)

}
object Permissions {
  val empty = Permissions(
    Tenant("root", Nil, Nil, None),
    Nil // TODO: Add support for user-defined tenants
  )
}

case class Tenant(
    id: String,
    rules: List[AccessRule],
    roles: List[Role],
    position: Option[PositionRange]
) extends Identifiable
    with Positioned

case class Role(
    user: PReference,
    rules: List[AccessRule],
    position: Option[PositionRange] = None
) extends PConstruct

sealed trait RuleKind
case object Allow extends RuleKind
case object Deny extends RuleKind

case class AccessRule(
    ruleKind: RuleKind,
    resourcePath: (PShape, Option[PShapeField]),
    actions: List[PPermission],
    predicate: Option[PFunctionValue[JsValue, Try[JsValue]]],
    position: Option[PositionRange]
) extends PConstruct {
  def eventsThatMatch: List[PEvent] = actions.flatMap(eventsOf).distinct

  def eventsOf(permission: PPermission): List[PEvent] =
    if (!actions.contains(permission)) Nil
    else
      (resourcePath, permission) match {
        case ((_, None), Create)             => List(Create, CreateMany)
        case ((_, Some(field)), SetOnCreate) => List(Create, CreateMany)
        case (_, Read)                       => List(Read, ReadMany)
        case ((_, None), Update)             => List(Update, UpdateMany)
        case ((_, Some(field)), Mutate)
            if field.ptype.isInstanceOf[PReference] =>
          List(Update, UpdateMany)
        case ((_, Some(field)), PushTo) =>
          List(PushTo(field), PushManyTo(field))
        case ((_, Some(field)), RemoveFrom) =>
          List(RemoveFrom(field), RemoveManyFrom(field))
        case ((_, None), Delete) => List(Delete, DeleteMany)
        case ((_, None), Login)  => List(Login)
        case ((_, Some(field)), All) if field.ptype.isInstanceOf[PArray] =>
          List(
            PushTo(field),
            PushManyTo(field),
            RemoveFrom(field),
            RemoveManyFrom(field),
            Update
          )
        case ((_, Some(field)), All)
            if field.ptype.isInstanceOf[PrimitiveType] ||
              field.ptype.isInstanceOf[PEnum] =>
          List(Read, ReadMany, Update, UpdateMany)
        case ((_, Some(field)), All) if field.ptype.isInstanceOf[PReference] =>
          List(Read, ReadMany, Update, UpdateMany)
        case ((_, None), All) =>
          List(
            Read,
            ReadMany,
            Update,
            UpdateMany,
            Create,
            CreateMany,
            Delete,
            DeleteMany,
            Login
          )
        case _ => Nil
      }
}

case class PConfig(values: List[ConfigEntry], position: Option[PositionRange])
    extends PConstruct {
  def getConfigEntry(key: String): Option[ConfigEntry] =
    values.find(configEntry => configEntry.key == key)
}

case class ConfigEntry(
    key: String,
    value: PValue,
    position: Option[PositionRange]
) extends Positioned

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
