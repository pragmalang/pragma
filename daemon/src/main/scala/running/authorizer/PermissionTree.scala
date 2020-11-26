package running.authorizer

import pragma.domain._
import running.operations._
import cats.implicits._
import spray.json.JsObject

final class PermissionTree(st: SyntaxTree) {
  private type TargetModelId = String
  private type TargetFieldId = String
  private type RoleId = String
  private type OnSelf = Boolean

  private val Permissions(globalTenant, _) = st.permissions

  private[authorizer] type Tree =
    Map[
      Option[RoleId],
      Map[
        TargetModelId,
        Map[
          Option[TargetFieldId],
          Map[
            PPermission,
            Map[
              OnSelf,
              Seq[AccessRule]
            ]
          ]
        ]
      ]
    ]

  private[authorizer] val tree: Tree = {
    val rolePairs = globalTenant.roles.map { role =>
      role.user.id.some -> targetModelTree(role.rules)
    } appended (None -> targetModelTree(globalTenant.rules))

    rolePairs.toMap.withDefaultValue {
      Map.empty
        .withDefaultValue {
          Map.empty
            .withDefaultValue {
              Map.empty
                .withDefaultValue {
                  Map.empty.withDefaultValue(Seq.empty[AccessRule])
                }
            }
        }
    }
  }

  private def targetModelTree(
      rules: Seq[AccessRule]
  ): Map[TargetModelId, Map[Option[TargetFieldId], Map[
    PPermission,
    Map[OnSelf, Seq[AccessRule]]
  ]]] =
    rules
      .groupBy(_.resourcePath._1.id)
      .view
      .mapValues(targetFieldTree)
      .toMap
      .withDefaultValue {
        Map.empty
          .withDefaultValue {
            Map.empty
              .withDefaultValue {
                Map.empty.withDefaultValue(Seq.empty[AccessRule])
              }
          }
      }

  private def targetFieldTree(
      modelRules: Seq[AccessRule]
  ): Map[Option[TargetFieldId], Map[PPermission, Map[OnSelf, Seq[
    AccessRule
  ]]]] =
    modelRules
      .groupBy(_.resourcePath._2.map(_.id))
      .view
      .mapValues(permissionTree)
      .toMap
      .withDefaultValue {
        Map.empty
          .withDefaultValue {
            Map.empty.withDefaultValue(Seq.empty[AccessRule])
          }
      }

  private def permissionTree(
      rules: Seq[AccessRule]
  ): Map[PPermission, Map[OnSelf, Seq[AccessRule]]] =
    PermissionTree.allPermissions
      .map { permission =>
        permission -> rules
          .filter(_.permissions(permission))
          .groupBy(_.isSlefRule)
          .withDefaultValue(Seq.empty)
      }
      .toMap[PPermission, Map[OnSelf, Seq[AccessRule]]]
      .withDefaultValue {
        Map.empty.withDefaultValue(Seq.empty[AccessRule])
      }

  /** Returns all the rules that apply to `op` *excluding* inner read rules */
  def rulesOf(op: Operation): Seq[AccessRule] = {
    val role = op.user.map(_._1.role)
    val targetFieldTree = tree(role)(op.targetModel.id)
    op match {
      case _: ReadOperation =>
        if (op.targetsSelf)
          targetFieldTree(None)(Read)(false) ++
            targetFieldTree(None)(Read)(true)
        else
          targetFieldTree(None)(Read)(false)
      case _: ReadManyOperation => targetFieldTree(None)(Read)(false)
      case createOp: CreateOperation =>
        targetFieldTree(None)(Create)(false) ++
          createOp.opArguments.obj.fields.keys.flatMap { fieldId =>
            targetFieldTree(fieldId.some)(SetOnCreate)(false)
          }.toSeq
      case createManyOp: CreateManyOperation =>
        targetFieldTree(None)(Create)(false) ++
          createManyOp.opArguments.items.flatMap { obj =>
            obj.fields.keys.flatMap { fieldId =>
              targetFieldTree(fieldId.some)(SetOnCreate)(false)
            }
          }.distinct
      case updateOp: UpdateOperation =>
        if (updateOp.targetsSelf)
          targetFieldTree(None)(Update)(false) ++
            targetFieldTree(None)(Update)(true) ++
            updateOp.opArguments.obj.obj.fields.keys.flatMap { fieldId =>
              targetFieldTree(fieldId.some)(Update)(false) ++
                targetFieldTree(fieldId.some)(Update)(true)
            }.toSeq
        else
          targetFieldTree(None)(Update)(false) ++
            updateOp.opArguments.obj.obj.fields.keys.flatMap { fieldId =>
              targetFieldTree(fieldId.some)(Update)(false)
            }.toSeq
      case updateManyOp: UpdateManyOperation =>
        targetFieldTree(None)(Update)(false) ++
          updateManyOp.opArguments.items.flatMap { obj =>
            obj.obj.fields.keys.flatMap { fieldId =>
              targetFieldTree(fieldId.some)(Update)(false)
            }
          }.distinct
      case _: DeleteOperation =>
        targetFieldTree(None)(Delete)(op.targetsSelf)
      case _: DeleteManyOperation => targetFieldTree(None)(Delete)(false)
      case pushToOp: PushToOperation =>
        (pushToOp.arrayField.ptype, pushToOp.opArguments.item) match {
          case (PReference(refId), JsObject(fields))
              if fields.size > 1 && pushToOp.targetsSelf &&
                fields.contains(st.modelsById(refId).primaryField.id) =>
            tree(role)(refId)(None)(Create)(false) ++
              targetFieldTree(pushToOp.arrayField.id.some)(Mutate)(false) ++
              targetFieldTree(pushToOp.arrayField.id.some)(PushTo)(false) ++
              targetFieldTree(pushToOp.arrayField.id.some)(Mutate)(true) ++
              targetFieldTree(pushToOp.arrayField.id.some)(PushTo)(true)
          case (PReference(refId), JsObject(fields))
              if fields.size > 1 &&
                fields.contains(st.modelsById(refId).primaryField.id) =>
            tree(role)(refId)(None)(Create)(false) ++
              targetFieldTree(pushToOp.arrayField.id.some)(Mutate)(false) ++
              targetFieldTree(pushToOp.arrayField.id.some)(PushTo)(false)
          case _ if pushToOp.targetsSelf =>
            targetFieldTree(pushToOp.arrayField.id.some)(Mutate)(false) ++
              targetFieldTree(pushToOp.arrayField.id.some)(PushTo)(false) ++
              targetFieldTree(pushToOp.arrayField.id.some)(Mutate)(true) ++
              targetFieldTree(pushToOp.arrayField.id.some)(PushTo)(true)
          case _ =>
            targetFieldTree(pushToOp.arrayField.id.some)(Mutate)(false) ++
              targetFieldTree(pushToOp.arrayField.id.some)(PushTo)(false)
        }
      case pushManyToOp: PushManyToOperation => {
        pushManyToOp.arrayField.ptype match {
          case PArray(PReference(refId)) => {
            val createExists = pushManyToOp.opArguments.items.exists {
              case JsObject(fields) =>
                fields.size > 1 &&
                  fields.contains(st.modelsById(refId).primaryField.id)
              case _ => false
            }
            if (createExists && pushManyToOp.targetsSelf)
              tree(role)(refId)(None)(Create)(false) ++
                targetFieldTree(pushManyToOp.arrayField.id.some)(Mutate)(false) ++
                targetFieldTree(pushManyToOp.arrayField.id.some)(PushTo)(false) ++
                targetFieldTree(pushManyToOp.arrayField.id.some)(PushTo)(true) ++
                targetFieldTree(pushManyToOp.arrayField.id.some)(PushTo)(true)
            else if (createExists)
              tree(role)(refId)(None)(Create)(false) ++
                targetFieldTree(pushManyToOp.arrayField.id.some)(Mutate)(false) ++
                targetFieldTree(pushManyToOp.arrayField.id.some)(PushTo)(false)
            else
              targetFieldTree(pushManyToOp.arrayField.id.some)(Mutate)(false) ++
                targetFieldTree(pushManyToOp.arrayField.id.some)(PushTo)(false)
          }
          case _ if pushManyToOp.targetsSelf =>
            targetFieldTree(pushManyToOp.arrayField.id.some)(Mutate)(false) ++
              targetFieldTree(pushManyToOp.arrayField.id.some)(PushTo)(false) ++
              targetFieldTree(pushManyToOp.arrayField.id.some)(Mutate)(true) ++
              targetFieldTree(pushManyToOp.arrayField.id.some)(Mutate)(true)
          case _ =>
            targetFieldTree(pushManyToOp.arrayField.id.some)(Mutate)(true) ++
              targetFieldTree(pushManyToOp.arrayField.id.some)(Mutate)(true)
        }
      }
      case removeFromOp: RemoveFromOperation =>
        if (removeFromOp.targetsSelf)
          targetFieldTree(removeFromOp.arrayField.id.some)(Mutate)(false) ++
            targetFieldTree(removeFromOp.arrayField.id.some)(RemoveFrom)(false) ++
            targetFieldTree(removeFromOp.arrayField.id.some)(Mutate)(true) ++
            targetFieldTree(removeFromOp.arrayField.id.some)(RemoveFrom)(true)
        else
          targetFieldTree(removeFromOp.arrayField.id.some)(Mutate)(false) ++
            targetFieldTree(removeFromOp.arrayField.id.some)(RemoveFrom)(false)
      case rmManyFromOp: RemoveManyFromOperation =>
        if (rmManyFromOp.targetsSelf)
          targetFieldTree(rmManyFromOp.arrayField.id.some)(Mutate)(false) ++
            targetFieldTree(rmManyFromOp.arrayField.id.some)(RemoveFrom)(false) ++
            targetFieldTree(rmManyFromOp.arrayField.id.some)(Mutate)(true) ++
            targetFieldTree(rmManyFromOp.arrayField.id.some)(RemoveFrom)(true)
        else
          targetFieldTree(rmManyFromOp.arrayField.id.some)(Mutate)(false) ++
            targetFieldTree(rmManyFromOp.arrayField.id.some)(RemoveFrom)(false)
      case _: LoginOperation =>
        AccessRule(Allow, (op.targetModel, None), Set(Login), None, false, None) +:
          targetFieldTree(None)(Login)(false)
      case _ => Seq.empty
    }
  }

  /** @return The sequence of rules that apply to the argument's inner read operations. */
  def innerReadRules(op: Operation): Seq[AccessRule] =
    if (op.innerReadOps.isEmpty) Seq.empty
    else {
      val targetModelTree =
        tree(op.user.map(_._1.role))(op.targetModel.id)
      op match {
        case _: CreateOperation | _: CreateManyOperation =>
          targetModelTree(None)(Read)(false) ++
            op.innerReadOps.flatMap { iop =>
              val targetFieldTree =
                targetModelTree(iop.targetField.field.id.some)
              targetFieldTree(Read)(false) ++
                targetFieldTree(ReadOnCreate)(false)
            } ++
            targetModelTree(None)(ReadOnCreate)(false) :+
            AccessRule(
              Allow,
              (op.targetModel, None),
              Set(ReadOnCreate),
              None,
              false,
              None
            )
        case iop: InnerReadOperation =>
          targetModelTree(None)(Read)(false) ++
            targetModelTree(iop.targetField.field.id.some)(Read)(false)
        case imop: InnerReadManyOperation =>
          targetModelTree(None)(Read)(false) ++
            targetModelTree(imop.targetField.field.id.some)(Read)(false)
        case _ if op.targetsSelf =>
          targetModelTree(None)(Read)(false) ++
            targetModelTree(None)(Read)(true) ++
            op.innerReadOps.flatMap { iop =>
              val targetFieldTree =
                targetModelTree(iop.targetField.field.id.some)(Read)
              targetFieldTree(false) ++ targetFieldTree(true)
            }
        case _ =>
          targetModelTree(None)(Read)(false) ++
            op.innerReadOps.flatMap { iop =>
              val targetFieldTree =
                targetModelTree(iop.targetField.field.id.some)(Read)
              targetFieldTree(false) ++ targetFieldTree(true)
            }
      }
    }

}
object PermissionTree {

  private val allPermissions = List(
    Read,
    Create,
    SetOnCreate,
    ReadOnCreate,
    Update,
    Delete,
    Mutate,
    PushTo,
    RemoveFrom,
    Login
  )

}
