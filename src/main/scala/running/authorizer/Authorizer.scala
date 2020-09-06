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
    ops.flatMap(opResults(_, predicateArg))

  def opResults(
      op: Operation,
      predicateArg: JsValue
  ): Vector[AuthorizationError] = {
    val rules = permissionTree.rulesOf(op)

    val outerOpResults =
      rules.flatMap(outerOpResult(_, op, predicateArg)).toVector

    val innerOpResults = innerReadResults(op, predicateArg)

    outerOpResults ++ innerOpResults
  }

  /** Note: should only recieve a relevant rule (use `PermissionTree#rulesOf`) */
  private def outerOpResult(
      rule: AccessRule,
      op: Operation,
      predicateArg: JsValue
  ): Vector[AuthorizationError] = {
    lazy val inferredError = op.event match {
      case Create if rule.permissions.contains(SetOnCreate) =>
        AuthorizationError(
          s"Denied setting field `${rule.resourcePath._2.get.id}` in `CREATE` operation"
        )
      case Update | UpdateMany if rule.resourcePath._2.isDefined =>
        AuthorizationError(
          s"Denied updating `${rule.resourcePath._2.get.id}` field in `UPDATE` operation on `${rule.resourcePath._1.id}`"
        )
      case Update | UpdateMany =>
        AuthorizationError(
          s"Denied performing `${Update}` operation on `${rule.resourcePath._1.id}`"
        )
      case _ => AuthorizationError(s"`${op.event}` operation denied")
    }

    rule.ruleKind match {
      case Allow =>
        userPredicateResult(rule, predicateArg) match {
          case Left(err) => Vector(err)
          case Right(_)  => Vector.empty
        }
      case Deny =>
        userPredicateResult(rule, predicateArg) match {
          case Left(err)    => Vector(err)
          case Right(true)  => Vector(inferredError)
          case Right(false) => Vector.empty
        }
    }
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

}
