package running.authorizer

import domain._
import running._
import cats.implicits._

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
    val targetFieldTree = tree(op.user.map(_._1.role))(op.targetModel.id)
    op match {
      case _: ReadOperation =>
        targetFieldTree(None)(Read)(op.targetsSelf)
      case _: ReadManyOperation => targetFieldTree(None)(Read)(op.targetsSelf)
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
      case updateOp: UpdateOperation => {
        targetFieldTree(None)(Update)(op.targetsSelf) ++
          updateOp.opArguments.obj.obj.fields.keys.flatMap { fieldId =>
            targetFieldTree(fieldId.some)(Update)(op.targetsSelf)
          }.toSeq
      }
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
        targetFieldTree(pushToOp.arrayField.id.some)(Mutate)(op.targetsSelf) ++
          targetFieldTree(pushToOp.arrayField.id.some)(PushTo)(op.targetsSelf)
      case pushManyToOp: PushManyToOperation =>
        targetFieldTree(pushManyToOp.arrayField.id.some)(Mutate)(op.targetsSelf) ++
          targetFieldTree(pushManyToOp.arrayField.id.some)(PushTo)(
            op.targetsSelf
          )
      case removeFromOp: RemoveFromOperation =>
        targetFieldTree(removeFromOp.arrayField.id.some)(Mutate)(op.targetsSelf) ++
          targetFieldTree(removeFromOp.arrayField.id.some)(RemoveFrom)(
            op.targetsSelf
          )
      case removeManyFromOp: RemoveManyFromOperation => {
        targetFieldTree(removeManyFromOp.arrayField.id.some)(Mutate)(
          op.targetsSelf
        ) ++
          targetFieldTree(removeManyFromOp.arrayField.id.some)(RemoveFrom)(
            op.targetsSelf
          )
      }
      case _: LoginOperation => targetFieldTree(None)(Login)(false)
      case _                 => Seq.empty
    }
  }

  /** Takes an operation and one of its inner read children.
    * Returned is the sequence of rules that apply to the child inner op.
    * Note that the outer op can be an inner op itself.
    */
  def innerReadRules(
      outerOp: Operation,
      innerOp: InnerOperation
  ): Seq[AccessRule] = {
    val targetModelTree =
      tree(outerOp.user.map(_._1.role))(innerOp.targetModel.id)
    val targetFieldId = innerOp.targetField.field.id.some
    outerOp match {
      case _: CreateOperation | _: CreateManyOperation =>
        targetModelTree(None)(Read)(false) ++
          targetModelTree(targetFieldId)(ReadOnCreate)(false)
      case _ if outerOp.targetsSelf =>
        targetModelTree(None)(Read)(false) ++
          targetModelTree(None)(Read)(true)
        targetModelTree(targetFieldId)(Read)(false) ++
          targetModelTree(targetFieldId)(Read)(true)
      case _ =>
        targetModelTree(None)(Read)(false) ++
          targetModelTree(targetFieldId)(Read)(false)
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
