package running.authorizer

import domain._, domain.utils._
import running._, running.storage._
import spray.json._
import running.JwtPayload
import scala.util._
import cats.Monad
import cats.implicits._

class Authorizer[S, M[_]: Monad](
    syntaxTree: SyntaxTree,
    storage: Storage[S, M]
) {

  val permissionTree = new PermissionTree(syntaxTree)

  def apply(
      ops: Operations.OperationsMap,
      user: Option[JwtPayload]
  ): M[Vector[AuthorizationError]] = user match {
    case None =>
      results(ops.values.flatMap(_.values).flatten.toVector, JsNull).pure[M]
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

      user.map { userJson =>
        results(ops.values.flatMap(_.values).flatten.toVector, userJson)
      }
    }
  }

  def results(
      ops: Vector[Operation],
      predicateArg: JsValue
  ): Vector[AuthorizationError] =
    ops.flatMap { op =>
      outerOpResults(op, predicateArg) ++ innerReadResults(op, predicateArg)
    }

  private def outerOpResults(
      op: Operation,
      predicateArg: JsValue
  ): Vector[AuthorizationError] = {
    val (allows, denies) = permissionTree
      .rulesOf(op)
      .view
      .map(rule => rule -> userPredicateResult(rule, predicateArg))
      .filter {
        case (_, Right(bool)) => bool
        case _                => false
      }
      .partition(_._1.ruleKind == Allow)

    val allowErrors =
      if (allows.isEmpty) Vector {
        AuthorizationError(
          s"No `allow` rule exists that allows `${op.event}` operations on `${op.targetModel.id}`"
        )
      } else
        allows.collectFirst {
          case (_, Left(err)) => err
        } match {
          case Some(err) => Vector(err)
          case None      => Vector.empty
        }

    val denyErrors =
      if (denies.isEmpty) Vector.empty
      else
        denies.head match {
          case (rule, Right(_)) => Vector(inferredDenyError(op, rule))
          case (_, Left(err))   => Vector(err)
        }

    allowErrors ++ denyErrors
  }

  private def innerReadResults(
      op: Operation,
      predicateArg: JsValue
  ): Vector[AuthorizationError] =
    if (op.innerReadOps.isEmpty) Vector.empty
    else {
      val results = for {
        innerOp <- op.innerReadOps
        rules = permissionTree.innerReadRules(op, innerOp)
        (allows, denies) = rules.partition(_.ruleKind == Allow)
        allowExists = allows.exists { allow =>
          userPredicateResult(allow, predicateArg) match {
            case Right(value) => value
            case Left(_)      => false
          }
        }
        denyExists = denies.exists { deny =>
          userPredicateResult(deny, predicateArg) match {
            case Right(value) => value
            case Left(_)      => true
          }
        }
      } yield (innerOp, allowExists, denyExists)

      val roleStr = op.user.map(" for " + _._2.id).getOrElse("")

      val errors = results.collect {
        case (innerOp, false, false) =>
          AuthorizationError(
            s"No `allow` rule exists to allow `${innerOp.event}` operations on `${innerOp.displayTargetResource}`$roleStr"
          )
        case (innerOp, _, true) =>
          AuthorizationError(
            s"`deny` rule exists that prohibits `${innerOp.event}` operations on `${innerOp.displayTargetResource}`$roleStr",
            Some(
              "Try removing this rule if you would like this operation to be allowed"
            )
          )
      }

      val innerResults = op.innerReadOps.flatMap { innerOp =>
        innerReadResults(innerOp, predicateArg)
      }

      errors ++ innerResults
    }

  /**
    * Returns the boolean result of the user
    * predicate or the error thrown by the user
    */
  def userPredicateResult(
      rule: AccessRule,
      argument: JsValue
  ): Either[AuthorizationError, Boolean] = rule.predicate match {
    case None => Right(true)
    case Some(predicate) => {
      val predicateResult = predicate
        .execute(argument)
        .map {
          case JsTrue => true
          case _      => false
        }
      predicateResult match {
        case Success(value) => Right(value)
        case Failure(err)   => Left(AuthorizationError(err.getMessage))
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
