package running.authorizer

import pragma.domain._, utils._
import running.operations._, running.storage._
import spray.json._
import running.JwtPayload
import cats.Monad
import cats.implicits._
import cats.MonadError
import running.PFunctionExecutor
import cats.effect.Async

class Authorizer[S, M[_]: Async](
    syntaxTree: SyntaxTree,
    storage: Storage[S, M]
)(implicit MError: MonadError[M, Throwable]) {

  val permissionTree = new PermissionTree(syntaxTree)

  def apply(
      ops: Operations.OperationsMap,
      user: Option[JwtPayload]
  ): M[Vector[AuthorizationError]] = user match {
    case None =>
      results(ops.values.flatMap(_.values).flatten.toVector, JsObject.empty)
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

      val user = userReadQuery(userModel, jwt.userId)

      user.flatMap { userJson =>
        results(ops.values.flatMap(_.values).flatten.toVector, userJson)
      }
    }
  }

  def results(
      ops: Vector[Operation],
      userObj: JsObject
  ): M[Vector[AuthorizationError]] =
    ops.flatTraverse { op =>
      (
        outerOpResults(op, permissionTree.rulesOf(op).toList, userObj),
        innerReadResults(op, userObj)
      ) mapN (_ ++ _)
    }

  /** Returns only the results of the outer operation **/
  private def outerOpResults(
      op: Operation,
      rules: List[AccessRule],
      userObj: JsObject
  ): M[Vector[AuthorizationError]] =
    for {
      ruleResults <- rules
        .traverse(rule => userPredicateResult(rule, userObj).map(rule -> _))
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

  /** Returns only the results of inner read operations **/
  private def innerReadResults(
      op: Operation,
      userObj: JsObject
  ): M[Vector[AuthorizationError]] =
    op.innerReadOps.flatTraverse { iop =>
      for {
        iopResults <- outerOpResults(
          iop,
          permissionTree.innerReadRules(op, iop).toList,
          userObj
        )
        innerIopResults <- iop.innerReadOps.flatTraverse { innerIop =>
          outerOpResults(
            innerIop,
            permissionTree.innerReadRules(iop, innerIop).toList,
            userObj
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
        PFunctionExecutor
          .execute[M](predicate, userObject :: Nil)
          .map {
            case JsTrue => true
            case _      => false
          }
      predicateResult.recoverWith {
        case err: Throwable =>
          MError.raiseError(AuthorizationError(err.getMessage))
      }
    }
  }

  private def userReadQuery(
      userModel: PModel,
      userId: JsValue
  ): M[JsObject] =
    storage.queryEngine.runQuery(
      storage.queryEngine.readOneRecord(
        userModel,
        userId,
        Vector(Operations.primaryFieldInnerOp(userModel))
      )
    )

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
