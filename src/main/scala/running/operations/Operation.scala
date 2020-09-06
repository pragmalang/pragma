package running

import domain._
import running.storage.QueryWhere
import spray.json._
import scala.util.Try
import cats.implicits._

sealed trait Operation {
  val event: PEvent
  val opArguments: OpArgs[PEvent]
  val targetModel: PModel
  val user: Option[(JwtPayload, PModel)]
  val targetsSelf: Boolean
  // Contains hooks used in @onRead, @onWrite, and @onDelete directives
  val hooks: Seq[PFunctionValue[JsValue, Try[JsValue]]]
  val name: String
  val groupName: String
  val innerReadOps: Vector[InnerOperation]
}

case class ReadOperation(
    opArguments: ReadArgs,
    targetModel: PModel,
    user: Option[(JwtPayload, PModel)],
    hooks: Seq[PFunctionValue[JsValue, Try[JsValue]]],
    name: String,
    groupName: String,
    innerReadOps: Vector[InnerOperation]
) extends Operation {
  override val event = Read
  override val targetsSelf = this.opArguments.id.some == user.map(_._1.userId)
}

case class ReadManyOperation(
    opArguments: ReadManyArgs,
    targetModel: PModel,
    user: Option[(JwtPayload, PModel)],
    hooks: Seq[PFunctionValue[JsValue, Try[JsValue]]],
    name: String,
    groupName: String,
    innerReadOps: Vector[InnerOperation]
) extends Operation {
  override val event = ReadMany
  override val targetsSelf = false
}

case class CreateOperation(
    opArguments: CreateArgs,
    targetModel: PModel,
    user: Option[(JwtPayload, PModel)],
    hooks: Seq[PFunctionValue[JsValue, Try[JsValue]]],
    name: String,
    groupName: String,
    innerReadOps: Vector[InnerOperation]
) extends Operation {
  override val event = Create
  override val targetsSelf = false
}

case class CreateManyOperation(
    opArguments: CreateManyArgs,
    targetModel: PModel,
    user: Option[(JwtPayload, PModel)],
    hooks: Seq[PFunctionValue[JsValue, Try[JsValue]]],
    name: String,
    groupName: String,
    innerReadOps: Vector[InnerOperation]
) extends Operation {
  override val event = CreateMany
  override val targetsSelf = false
}

case class UpdateOperation(
    opArguments: UpdateArgs,
    targetModel: PModel,
    user: Option[(JwtPayload, PModel)],
    hooks: Seq[PFunctionValue[JsValue, Try[JsValue]]],
    name: String,
    groupName: String,
    innerReadOps: Vector[InnerOperation]
) extends Operation {
  override val event = Update
  override val targetsSelf =
    this.opArguments.obj.objId.some == user.map(_._1.userId)
}

case class UpdateManyOperation(
    opArguments: UpdateManyArgs,
    targetModel: PModel,
    user: Option[(JwtPayload, PModel)],
    hooks: Seq[PFunctionValue[JsValue, Try[JsValue]]],
    name: String,
    groupName: String,
    innerReadOps: Vector[InnerOperation]
) extends Operation {
  override val event = UpdateMany
  override val targetsSelf = false
}

case class DeleteOperation(
    opArguments: DeleteArgs,
    targetModel: PModel,
    user: Option[(JwtPayload, PModel)],
    hooks: Seq[PFunctionValue[JsValue, Try[JsValue]]],
    name: String,
    groupName: String,
    innerReadOps: Vector[InnerOperation]
) extends Operation {
  override val event = Delete
  override val targetsSelf = this.opArguments.id.some == user.map(_._1.userId)
}

case class DeleteManyOperation(
    opArguments: DeleteManyArgs,
    targetModel: PModel,
    user: Option[(JwtPayload, PModel)],
    hooks: Seq[PFunctionValue[JsValue, Try[JsValue]]],
    name: String,
    groupName: String,
    innerReadOps: Vector[InnerOperation]
) extends Operation {
  override val event = DeleteMany
  override val targetsSelf = false
}

case class PushToOperation(
    opArguments: PushToArgs,
    targetModel: PModel,
    user: Option[(JwtPayload, PModel)],
    hooks: Seq[PFunctionValue[JsValue, Try[JsValue]]],
    name: String,
    groupName: String,
    innerReadOps: Vector[InnerOperation],
    arrayField: PModelField
) extends Operation {
  override val event = PushTo(arrayField)
  override val targetsSelf = this.opArguments.id.some == user.map(_._1.userId)
}

case class PushManyToOperation(
    opArguments: PushManyToArgs,
    targetModel: PModel,
    user: Option[(JwtPayload, PModel)],
    hooks: Seq[PFunctionValue[JsValue, Try[JsValue]]],
    name: String,
    groupName: String,
    innerReadOps: Vector[InnerOperation],
    arrayField: PModelField
) extends Operation {
  override val event = PushManyTo(arrayField)
  override val targetsSelf = this.opArguments.id.some == user.map(_._1.userId)
}

case class RemoveFromOperation(
    opArguments: RemoveFromArgs,
    targetModel: PModel,
    user: Option[(JwtPayload, PModel)],
    hooks: Seq[PFunctionValue[JsValue, Try[JsValue]]],
    name: String,
    groupName: String,
    innerReadOps: Vector[InnerOperation],
    arrayField: PModelField
) extends Operation {
  override val event = RemoveFrom(arrayField)
  override val targetsSelf = this.opArguments.id.some == user.map(_._1.userId)
}

case class RemoveManyFromOperation(
    opArguments: RemoveManyFromArgs,
    targetModel: PModel,
    user: Option[(JwtPayload, PModel)],
    hooks: Seq[PFunctionValue[JsValue, Try[JsValue]]],
    name: String,
    groupName: String,
    innerReadOps: Vector[InnerOperation],
    arrayField: PModelField
) extends Operation {
  override val event = RemoveManyFrom(arrayField)
  override val targetsSelf = this.opArguments.id.some == user.map(_._1.userId)
}

case class LoginOperation(
    opArguments: LoginArgs,
    targetModel: PModel,
    user: Option[(JwtPayload, PModel)],
    hooks: Seq[PFunctionValue[JsValue, Try[JsValue]]],
    name: String,
    groupName: String,
    innerReadOps: Vector[InnerOperation]
) extends Operation {
  override val event = Login
  override val targetsSelf = false
}

/** Represents a data read selection within an operation */
sealed trait InnerOperation extends Operation {

  val targetField: Operations.AliasedField

  val nameOrAlias = targetField.alias.getOrElse(targetField.field.id)

  def displayTargetResource =
    targetModel.id + "." + targetField.field.id

}

case class InnerReadOperation(
    targetField: Operations.AliasedField,
    targetModel: PModel,
    user: Option[(JwtPayload, PModel)],
    hooks: Seq[PFunctionValue[JsValue, Try[JsValue]]],
    innerReadOps: Vector[InnerOperation]
) extends InnerOperation {
  override val event = Read
  override val opArguments = InnerOpNoArgs
  override val name = targetField.alias.getOrElse(targetField.field.id)
  override val groupName = "InnerReadOps"
  override val targetsSelf = false
}

case class InnerReadManyOperation(
    targetField: Operations.AliasedField,
    opArguments: InnerListArgs,
    targetModel: PModel,
    user: Option[(JwtPayload, PModel)],
    hooks: Seq[PFunctionValue[JsValue, Try[JsValue]]],
    innerReadOps: Vector[InnerOperation]
) extends InnerOperation {
  override val event = ReadMany
  override val name = targetField.alias.getOrElse(targetField.field.id)
  override val groupName = "InnerReadManyOps"
  override val targetsSelf = false
}

sealed trait OpArgs[+E <: PEvent]

/** An object with its extracted primary field value */
case class ObjectWithId(obj: JsObject, objId: JsValue)

case class ReadArgs(id: JsValue) extends OpArgs[Read.type]

case class ReadManyArgs(where: Option[QueryWhere]) extends OpArgs[ReadMany.type]

sealed trait InnerOpArgs[+R <: ReadEvent] extends OpArgs[R]
case object InnerOpNoArgs extends InnerOpArgs[Read.type]
case class InnerListArgs(where: Option[QueryWhere])
    extends InnerOpArgs[ReadMany.type]

case class CreateArgs(obj: JsObject) extends OpArgs[Create.type]

case class CreateManyArgs(items: Seq[JsObject]) extends OpArgs[CreateMany.type]

case class UpdateArgs(obj: ObjectWithId) extends OpArgs[Update.type]

case class UpdateManyArgs(items: Seq[ObjectWithId])
    extends OpArgs[UpdateMany.type]

case class DeleteArgs(id: JsValue) extends OpArgs[Delete.type]

case class DeleteManyArgs(ids: Seq[JsValue]) extends OpArgs[DeleteMany.type]

case class PushToArgs(id: JsValue, item: JsValue) extends OpArgs[PushTo]

case class PushManyToArgs(id: JsValue, items: Seq[JsValue])
    extends OpArgs[PushManyTo]

case class RemoveFromArgs(id: JsValue, item: JsValue) extends OpArgs[RemoveFrom]

case class RemoveManyFromArgs(id: JsValue, items: Vector[JsValue])
    extends OpArgs[RemoveManyFrom]

case class LoginArgs(
    publicCredentialField: PModelField,
    publicCredentialValue: JsValue,
    secretCredentialValue: Option[String]
) extends OpArgs[Login.type]
