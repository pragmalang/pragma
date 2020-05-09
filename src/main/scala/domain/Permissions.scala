package domain

import domain.utils._
import spray.json._
import scala.util.Try

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
  lazy val allowedArrayFieldPermissions: Seq[PPermission] =
    List(Read, Update, SetOnCreate, PushTo, RemoveFrom, Mutate)
  lazy val allowedPrimitiveFieldPermissions: Seq[PPermission] =
    List(Read, Update, SetOnCreate)
  lazy val allowedModelPermissions: Seq[PPermission] =
    List(Read, Update, Create, Delete)
  lazy val allowedModelFieldPermissions: Seq[PPermission] =
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
    tenants: Seq[Tenant]
) {
  type TargetModelId = String
  type RoleId = String
  type PermissionTree =
    Map[Option[RoleId], Map[TargetModelId, Map[PEvent, Seq[AccessRule]]]]

  /**
    * A queryable tree of permissions.
    * Note: it's better to use the methods on `Permissions`
    * for querying instead of directly accessing `tree`.
    */
  lazy val tree: PermissionTree = constructTree

  private def ruleEventTree(
      rules: Seq[AccessRule]
  ): Map[PEvent, Seq[AccessRule]] = {
    val eventRulePairs =
      rules.flatMap(rule => rule.eventsThatMatch.map((_, rule)))
    eventRulePairs.groupMap(_._1)(_._2)
  }

  private def targetModelTree(
      rules: Seq[AccessRule]
  ): Map[TargetModelId, Map[PEvent, Seq[AccessRule]]] =
    rules.groupBy(_.resourcePath._1.id).map {
      case (targetModelId, rules) => (targetModelId, ruleEventTree(rules))
    }

  private def constructTree: PermissionTree = {
    val trees =
      globalTenant.roles.map { role =>
        Option(role.user.id) -> targetModelTree(
          globalTenant.rules ++ role.rules
        )
      }
    trees.toMap.withDefaultValue(targetModelTree(globalTenant.rules))
  }

  def rulesOf(
      role: Option[RoleId],
      targetModel: TargetModelId,
      event: PEvent
  ): Seq[AccessRule] = {
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
  ): Seq[AccessRule] =
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
    rules: Seq[AccessRule],
    roles: Seq[Role],
    position: Option[PositionRange]
) extends Identifiable
    with Positioned

case class Role(
    user: PReference,
    rules: Seq[AccessRule],
    position: Option[PositionRange] = None
) extends PConstruct

sealed trait RuleKind
case object Allow extends RuleKind
case object Deny extends RuleKind

case class AccessRule(
    ruleKind: RuleKind,
    resourcePath: (PShape, Option[PShapeField]),
    permissions: Set[PPermission],
    predicate: Option[PFunctionValue[JsValue, Try[JsValue]]],
    position: Option[PositionRange]
) extends PConstruct {
  def eventsThatMatch: Set[PEvent] = permissions.flatMap(eventsOf)

  def eventsOf(permission: PPermission): Seq[PEvent] =
    if (!permissions.contains(permission)) Nil
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
