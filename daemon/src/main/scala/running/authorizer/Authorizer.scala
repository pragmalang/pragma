package running.authorizer

import pragma.domain._, utils._, pragma.domain.DomainImplicits._
import running.operations._, running.storage._
import spray.json._
import running.JwtPayload
import cats.Monad
import cats.implicits._
import cats.MonadError
import running.PFunctionExecutor
import cats.effect.Async
import cats.effect.ConcurrentEffect
import running.operations.Operations.AliasedField

class Authorizer[S, M[_]: Async: ConcurrentEffect](
    syntaxTree: SyntaxTree,
    storage: Storage[S, M],
    funcExecutor: PFunctionExecutor[M]
)(implicit MError: MonadError[M, Throwable]) {

  val permissionTree = new PermissionTree(syntaxTree)

  def apply(
      ops: Operations.OperationsMap,
      user: Option[JwtPayload]
  ): M[Vector[AuthorizationError]] = user match {
    case None =>
      results(ops.values.flatMap(_.values).flatten.toVector, None)
    case Some(jwt) => {
      val userModel = syntaxTree.modelsById.get(jwt.role) match {
        case Some(model) => model
        case _ =>
          return Monad[M].pure {
            Vector {
              AuthorizationError(
                s"Request has role `${jwt.role}` that doesn't exist"
              )
            }
          }
      }

      val user = shallowReadQuery(userModel, jwt.userId)

      user.flatMap { userJson =>
        results(
          ops.values.flatMap(_.values).flatten.toVector,
          Some((userModel, userJson))
        )
      }
    }
  }

  def results(
      ops: Vector[Operation],
      user: Option[(PModel, JsObject)]
  ): M[Vector[AuthorizationError]] =
    ops.flatTraverse { op =>
      (
        outerOpResults(op, permissionTree.rulesOf(op).toList, user),
        innerReadResults(op, user)
      ) mapN (_ ++ _)
    }

  /** Returns only the results of the outer operation **/
  private def outerOpResults(
      op: Operation,
      rules: List[AccessRule],
      user: Option[(PModel, JsObject)]
  ): M[Vector[AuthorizationError]] = {
    val opArgJson: Option[M[JsValue]] = op.opArguments match {
      case ReadArgs(id) =>
        shallowReadQuery(op.targetModel, id).widen[JsValue].some
      case ReadManyArgs(_)   => None
      case InnerOpNoArgs     => None
      case InnerListArgs(_)  => None
      case CreateArgs(obj)   => obj.pure[M].widen[JsValue].some
      case CreateManyArgs(_) => None
      case UpdateArgs(obj)   => obj.obj.pure[M].widen[JsValue].some
      case UpdateManyArgs(_) => None
      case DeleteArgs(id) =>
        shallowReadQuery(op.targetModel, id).widen[JsValue].some
      case DeleteManyArgs(_)        => None
      case PushToArgs(_, item)      => item.pure[M].some
      case PushManyToArgs(_, _)     => None
      case RemoveFromArgs(_, item)  => item.pure[M].some
      case RemoveManyFromArgs(_, _) => None
      case LoginArgs(_, _, _)       => None
    }

    val predicateInput: M[JsObject] = (opArgJson, user) match {
      case (Some(opArg), Some((userModel, userObj))) =>
        opArg.map { arg =>
          JsObject(
            userModel.id.small -> userObj,
            op.targetModel.id.small -> arg
          )
        }
      case (Some(opArg), None) =>
        opArg.map(arg => JsObject(op.targetModel.id.small -> arg))
      case (None, Some((userModel, userObj))) =>
        JsObject(userModel.id.small -> userObj).pure[M]
      case (None, None) => JsObject.empty.pure[M]
    }

    for {
      ruleResults <- rules
        .traverse { rule =>
          predicateInput
            .flatMap(in => userPredicateResult(rule, in))
            .map(rule -> _)
        }
        .map(_.partition(_._1.ruleKind == Allow))
      (allows, denies) = ruleResults
      allowErrors = if (allows.exists(_._2)) Vector.empty
      else
        Vector {
          AuthorizationError(
            s"No `allow` rule exists that allows `${op.event}` operations on `${op.targetModel.id}`"
          )
        }

      denyErrors = denies.collect {
        case (rule, true) => inferredDenyError(op, rule)
      }
    } yield allowErrors ++ denyErrors
  }

  /** Returns only the results of inner read operations **/
  private def innerReadResults(
      op: Operation,
      user: Option[(PModel, JsObject)]
  ): M[Vector[AuthorizationError]] =
    op.innerReadOps.flatTraverse { iop =>
      for {
        iopResults <- outerOpResults(
          iop,
          permissionTree.innerReadRules(op, iop).toList,
          user
        )
        innerIopResults <- iop.innerReadOps.flatTraverse { innerIop =>
          outerOpResults(
            innerIop,
            permissionTree.innerReadRules(iop, innerIop).toList,
            user
          )
        }
      } yield iopResults ++ innerIopResults
    }

  /**
    * Returns the boolean result of the user
    * predicate or the error thrown by the user
    */
  def userPredicateResult(
      rule: AccessRule,
      userObject: JsObject
  ): M[Boolean] = rule.predicate match {
    case None => true.pure[M]
    case Some(predicate) => {
      val predicateResult =
        funcExecutor
          .execute(predicate, userObject)
          .map {
            case o: JsObject => o.fields.get("result") == Some(JsTrue)
            case _           => false
          }

      predicateResult.recoverWith {
        case err: Throwable =>
          MError.raiseError(AuthorizationError(err.getMessage))
      }
    }
  }

  private def shallowReadQuery(
      targetModel: PModel,
      targetId: JsValue
  ): M[JsObject] = {
    val innerOps = targetModel.fields.toVector.map {
      case f @ PModelField(_, PReference(refId), _, _, _, _) => {
        val refModel = syntaxTree.modelsById(refId)
        InnerReadOperation(
          AliasedField(f, None, Vector.empty),
          refModel,
          None,
          Seq.empty,
          Vector(Operations.primaryFieldInnerOp(refModel))
        )
      }
      case f @ PModelField(_, POption(PReference(refId)), _, _, _, _) => {
        val refModel = syntaxTree.modelsById(refId)
        InnerReadOperation(
          AliasedField(f, None, Vector.empty),
          refModel,
          None,
          Seq.empty,
          Vector(Operations.primaryFieldInnerOp(refModel))
        )
      }
      case f @ PModelField(_, PArray(PReference(refId)), _, _, _, _) => {
        val refModel = syntaxTree.modelsById(refId)
        InnerReadManyOperation(
          AliasedField(f, None, Vector.empty),
          InnerListArgs(None),
          refModel,
          None,
          Seq.empty,
          Vector(Operations.primaryFieldInnerOp(refModel))
        )
      }
      case f @ PModelField(_, PArray(_), _, _, _, _) =>
        InnerReadManyOperation(
          AliasedField(f, None, Vector.empty),
          InnerListArgs(None),
          targetModel,
          None,
          Seq.empty,
          Vector.empty
        )
      case f =>
        InnerReadOperation(
          AliasedField(f, None, Vector.empty),
          targetModel,
          None,
          Seq.empty,
          Vector.empty
        )
    }

    storage.queryEngine.runQuery(
      storage.queryEngine.readOneRecord(
        targetModel,
        targetId,
        innerOps
      )
    )
  }

  private def inferredDenyError(op: Operation, denyRule: AccessRule) =
    op match {
      case iop: InnerReadOperation =>
        AuthorizationError(
          s"`deny` rule exists that prohibits `READ` operations on `${iop.targetModel.id}.${iop.targetField.field.id}`"
        )
      case _ =>
        op.event match {
          case Create if denyRule.permissions.contains(SetOnCreate) =>
            AuthorizationError(
              s"Denied setting field `${denyRule.resourcePath._2.get.id}` in `CREATE` operation"
            )
          case Update | UpdateMany if denyRule.resourcePath._2.isDefined =>
            AuthorizationError(
              s"Denied updating `${denyRule.resourcePath._2.get.id}` field in `UPDATE` operation on `${denyRule.resourcePath._1.id}`"
            )
          case Update | UpdateMany =>
            AuthorizationError(
              s"Denied performing `${Update}` operation on `${denyRule.resourcePath._1.id}`"
            )
          case _ => AuthorizationError(s"`${op.event}` operation denied")
        }
    }

}
