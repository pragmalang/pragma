package running

import domain._, domain.utils._, domain.DomainImplicits._
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
    val rules = relevantRules(op)

    val argsResult =
      rules.flatMap(opArgumentsResult(_, op, predicateArg)).toVector

    val innerOpResults = innerReadResults(op, predicateArg)

    argsResult ++ innerOpResults
  }

  /** Returns all the rules that can match */
  private def relevantRules(op: Operation): Seq[AccessRule] =
    syntaxTree.permissions.rulesOf(op.user.map(_._2), op.targetModel, op.event)

  /** Note: should only recieve a relevant rule (use `relevantRules`) */
  private def opArgumentsResult(
      rule: AccessRule,
      op: Operation,
      predicateArg: JsValue
  ): Vector[AuthorizationError] = {
    lazy val argsMatchRule = (rule.resourcePath._2, op) match {
      case (None, _) => true
      case (Some(ruleField), op: UpdateOperation) =>
        op.opArguments.obj.obj.fields.contains(ruleField.id)
      case (Some(ruleField), op: UpdateManyOperation) =>
        op.opArguments.items.exists { item =>
          item.obj.fields.contains(ruleField.id)
        }
      case (Some(ruleField), op: CreateOperation)
          if rule.permissions.contains(SetOnCreate) =>
        op.opArguments.obj.fields.contains(ruleField.id)
      case _ => false
    }

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

    lazy val result = rule.ruleKind match {
      case Allow =>
        userPredicateResult(rule, op, predicateArg) match {
          case Left(err) => Vector(err)
          case Right(_)  => Vector.empty
        }
      case Deny =>
        userPredicateResult(rule, op, predicateArg) match {
          case Left(err)    => Vector(err)
          case Right(true)  => Vector(inferredError)
          case Right(false) => Vector.empty
        }
    }

    if (argsMatchRule) result
    else Vector.empty
  }

  private def innerReadResults(
      op: Operation,
      predicateArg: JsValue
  ): Vector[AuthorizationError] =
    if (op.innerReadOps.isEmpty) Vector.empty
    else {
      val opIsOnSelf = op.user match {
        case Some(user) => opTargetsSelf(op, user)
        case None       => false
      }
      val results = for {
        innerOp <- op.innerReadOps
        rules = relevantRules(innerOp)
        (allows, denies) = rules.partition(_.ruleKind == Allow)
        allowExists = allows.exists { allow =>
          relevantRuleMatchesInnerReadOp(allow, innerOp, opIsOnSelf) &&
          (userPredicateResult(allow, op, predicateArg) match {
            case Right(value) => value
            case Left(_)      => false
          })
        }
        denyExists = denies.exists { deny =>
          relevantRuleMatchesInnerReadOp(deny, innerOp, opIsOnSelf) &&
          (userPredicateResult(deny, op, predicateArg) match {
            case Right(value) => value
            case Left(_)      => true
          })
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

  private def relevantRuleMatchesInnerReadOp(
      rule: AccessRule,
      innerOp: InnerOperation,
      onSelf: Boolean
  ): Boolean =
    rule.resourcePath._1.id == innerOp.targetModel.id &&
      (rule.resourcePath._2 match {
        case None            => true
        case Some(ruleField) => ruleField.id === innerOp.targetField.field.id
      }) && (if (rule.isSlefRule) onSelf else true)

  /**
    * Returns the boolean result of the user
    * predicate or the error thrown by the user
    */
  def userPredicateResult(
      rule: AccessRule,
      invokingOp: Operation,
      argument: JsValue
  ): Either[AuthorizationError, Boolean] = {
    lazy val userPredicateResult = rule.predicate match {
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

    val selfPredicateResult = invokingOp.user match {
      case Some(user) if rule.isSlefRule => opTargetsSelf(invokingOp, user)
      case _                             => true
    }

    userPredicateResult.map(selfPredicateResult && _)
  }

  private def opTargetsSelf(
      op: Operation,
      user: (JwtPayload, PModel)
  ): Boolean =
    user._1.role === op.targetModel.id &&
      (op match {
        case ro: ReadOperation         => ro.opArguments.id === user._1.userId
        case uo: UpdateOperation       => uo.opArguments.obj.objId === user._1.userId
        case pto: PushToOperation      => pto.opArguments.id === user._1.userId
        case pmto: PushManyToOperation => pmto.opArguments.id === user._1.userId
        case rofo: RemoveFromOperation => rofo.opArguments.id === user._1.userId
        case rmfo: RemoveManyFromOperation =>
          rmfo.opArguments.id == user._1.userId
        case _ => false
      })

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
