package pragma.domain

import pragma.domain.utils._
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
    case All          => "ALL"
    case SetOnCreate  => "SET_ON_CREATE"
    case ReadOnCreate => "READ_ON_CREATE"
    case Mutate       => "MUTATE"
    case PushTo       => "PUSH_TO"
    case RemoveFrom   => "REMOVE_FROM"
    case e: PEvent    => e.toString
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
case object ReadOnCreate extends PPermission
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
    val globalTenant: Tenant,
    val tenants: Seq[Tenant]
)
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
    predicate: Option[PFunctionValue],
    isSlefRule: Boolean,
    position: Option[PositionRange]
) extends PConstruct
