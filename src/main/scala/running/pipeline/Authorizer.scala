package running.pipeline.functions

import running.pipeline._
import domain._, Implicits.StringMethods
import domain.utils.InternalException
import domain.utils.AuthorizationError
import spray.json._
import setup.storage.Storage
import running.pipeline.Operations.ReadOperation
import sangria.ast._
import running.JwtPaylod
import domain.utils.UserError
import scala.util._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

case class Authorizer(
    syntaxTree: SyntaxTree,
    storage: Storage,
    devModeOn: Boolean = true
) {
  import Authorizer._

  def apply(request: Request): Future[AuthorizationResult] =
    (syntaxTree.permissions, request.user) match {
      case (None, _) =>
        Future.failed(
          UserError.fromAuthErrors(AuthorizationError("Access Denied") :: Nil)
        )
      case (Some(permissions), None) => {
        val reqOps = Operations.operationsFrom(request)(syntaxTree)
        Future(results(reqOps.values.flatten.toVector, JsNull))
      }
      case (Some(permissions), Some(jwt)) => {
        val userModel = syntaxTree.models.find(_.id == jwt.role) match {
          case Some(model) => model
          case _ =>
            return Future.failed(
              InternalException(
                s"Request has role `${jwt.role}` that doesn't exist"
              )
            )
        }

        val user = storage.run(
          userQuery(userModel, jwt.userId),
          Map(None -> Vector(userReadOperation(userModel, jwt)))
        )

        user.map {
          case Left(userJson) => {
            val reqOps = Operations.operationsFrom(request)(syntaxTree)
            results(reqOps.values.flatten.toVector, userJson)
          }
          case Right(_) =>
            throw InternalException(
              "User object retrieved from storage must be an object"
            )
        }
      }
    }

  def results(
      ops: Vector[Operation],
      predicateArg: JsValue
  ): AuthorizationResult = {
    val opsResult = ops.map(opResults(_, predicateArg))
    if (!devModeOn) Right {
      opsResult
        .collect {
          case Right(bool) => bool
        }
        .reduce(_ && _)
    } else
      Left {
        opsResult.collect {
          case Left(errors) => errors
        }.flatten
      }
  }

  def opResults(op: Operation, predicateArg: JsValue): AuthorizationResult = {
    val innerOpResults = innerReadResults(op, predicateArg)
    val rules = relevantRules(op)
    val rulesWhereArgsMatch = rules.filter(opArgumentsMatch(_, op))
    type Acc = (Option[AuthorizationResult], Option[AuthorizationResult])
    val predicateResult =
      rulesWhereArgsMatch.foldLeft[Acc]((None, None)) { (acc, rule) =>
        lazy val predicateResult = userPredicateResult(rule, predicateArg)
        (acc, predicateResult, rule.ruleKind) match {
          case ((None, None), Left(_), _) => (None, Some(predicateResult))
          case ((None, None), Right(true), Allow) =>
            (Some(predicateResult), None)
          case ((None, None), Right(true), Deny) =>
            (None, Some(predicateResult))
          case ((Some(allow), None), Right(true), Deny) =>
            (Some(allow), Some(predicateResult))
          case _ => acc
        }
      }
    (innerOpResults, predicateResult) match {
      case (Right(iopBool), (Some(Right(true)), None)) => Right(iopBool)
      case (Right(iopBool), (None, Some(Right(deny)))) =>
        Right(iopBool && !deny)
      case (Left(iopErrors), (None, Some(Left(denyErrors)))) =>
        Left(denyErrors ++ iopErrors)
      case _ => Right(false)
    }
  }

  /** Returns all the rules that can match */
  def relevantRules(op: Operation): List[AccessRule] =
    syntaxTree.permissions match {
      case None => Nil
      case Some(permissions) =>
        permissions.rulesOf(op.role, op.targetModel, op.event)
    }

  /** Note: should only recieve a relevant rule (use `relevantRules`) */
  def opArgumentsMatch(
      rule: AccessRule,
      op: Operation
  ): Boolean =
    ((rule.resourcePath._2, op.event) match {
      case (_, _: ReadEvent) => true
      case (Some(ruleField), Update) =>
        op.opArguments
          .find(_.name == op.targetModel.id.small)
          .map(_.value) match {
          case Some(ObjectValue(fields, _, _)) =>
            fields.exists(_.name == ruleField.id)
          case _ =>
            throw InternalException(
              "Argument passed to `update` is not an object. Something must've went wrond duing query validation or schema generation"
            )
        }
      case (Some(ruleField), UpdateMany) =>
        op.opArguments
          .find(_.name == "items")
          .map(_.value match {
            case ListValue(values, _, _) =>
              values.exists { value =>
                value.isInstanceOf[ObjectValue] &&
                value
                  .asInstanceOf[ObjectValue]
                  .fields
                  .exists(_.name == ruleField.id)
              }
            case _ => true
          })
          .getOrElse(true)
      case (Some(ruleField), Create) if rule.actions.contains(SetOnCreate) =>
        op.opArguments
          .find(_.name == op.targetModel.id.small)
          .map(_.value match {
            case ObjectValue(fields, _, _) =>
              fields.exists(_.name == ruleField.id)
            case _ => true
          })
          .getOrElse(true)
      case _ => true
    })

  /** Left: vector of errors when `devModeOn` (empty if all is OK).
    * Right: all is OK in production mode
    */
  def innerReadResults(
      op: Operation,
      predicateArg: JsValue
  ): AuthorizationResult =
    if (op.innerReadOps.isEmpty && !devModeOn) Right(true)
    else if (op.innerReadOps.isEmpty && devModeOn) Left(Vector.empty)
    else {
      val results = for {
        innerOp <- op.innerReadOps
        rules = relevantRules(innerOp.operation)
        (allows, denies) = rules.partition(_.ruleKind == Allow)
        allowExists = allows.exists { allow =>
          relevantRuleMatchesInnerReadOp(allow, innerOp) &&
          (userPredicateResult(allow, predicateArg) match {
            case Right(value) => value
            case _            => false
          })
        }
        denyExists = denies.exists { deny =>
          relevantRuleMatchesInnerReadOp(deny, innerOp) &&
          (userPredicateResult(deny, predicateArg) match {
            case Right(value) => value
            case _            => false
          })
        }
      } yield (innerOp, allowExists, denyExists)

      lazy val opIsAllowed = results.forall {
        case (_, allowExists, denyExists) => allowExists && !denyExists
      }

      lazy val errors = results.collect {
        case (innerOp, true, true) =>
          AuthorizationError(
            s"`deny` rule exists that prohibits `${innerOp.operation.event}` operations on `${innerOp.displayTargetResource}`",
            Some(
              "Try removing this rule if you would like this operation to be allowed"
            )
          )
        case (innerOp, false, false) =>
          AuthorizationError(
            s"No `allow` rule exists to allow `${innerOp.operation.event}` operations on `${innerOp.displayTargetResource}`",
            Some(
              s"Try adding the rule `allow ${PPermission
                .from(innerOp.operation.event)} ${innerOp.operation.targetModel.id}` to your permissions"
            )
          )
      }

      val innerResults = op.innerReadOps.map { innerOp =>
        innerReadResults(innerOp.operation, predicateArg)
      }

      if (!devModeOn) Right {
        opIsAllowed && innerResults
          .collect {
            case Right(value) => value
            case _            => false
          }
          .forall(value => value)
      } else
        Left {
          errors ++ innerResults.collect {
            case Left(innerErrors) => innerErrors
          }.flatten
        }
    }

  def relevantRuleMatchesInnerReadOp(
      rule: AccessRule,
      innerOp: InnerOperation
  ): Boolean =
    rule.resourcePath._1.id == innerOp.operation.targetModel.id &&
      (rule.resourcePath._2 match {
        case None            => true
        case Some(ruleField) => ruleField.id == innerOp.targetField.field.id
      })

}
object Authorizer {

  type AuthorizationResult = Either[Vector[AuthorizationError], Boolean]

  /**
    * Returns the boolean result of the user
    * predicate or the error thrown by the user
    */
  def userPredicateResult(
      rule: AccessRule,
      argument: JsValue
  ): AuthorizationResult =
    rule.predicate match {
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
          case Failure(err) =>
            Left(Vector(AuthorizationError(err.getMessage())))
        }
      }
    }

  def userReadOperation(role: PModel, jwt: JwtPaylod) =
    Operation(
      opKind = ReadOperation,
      gqlOpKind = sangria.ast.OperationType.Query,
      opArguments =
        Vector(Argument(role.primaryField.id, StringValue(jwt.userId))),
      directives = Vector.empty,
      event = Read,
      targetModel = role,
      role = Some(role),
      user = Some(jwt),
      crudHooks = Nil,
      alias = None,
      innerReadOps = Vector.empty
    )

  /** Construct the query to get the user to be authorized from storage */
  def userQuery(role: PModel, userId: String) =
    Document(
      Vector(
        OperationDefinition(
          OperationType.Query,
          None,
          Vector.empty,
          Vector.empty,
          Vector(
            Field(
              None,
              role.id,
              Vector.empty,
              Vector.empty,
              Vector(
                Field(
                  None,
                  "read",
                  Vector(
                    Argument(
                      role.primaryField.id,
                      StringValue(userId)
                    )
                  ),
                  Vector.empty,
                  role.fields.map { pfield =>
                    Field(
                      None,
                      pfield.id,
                      Vector.empty,
                      Vector.empty,
                      Vector.empty
                    )
                  }.toVector,
                  Vector.empty,
                  Vector.empty
                )
              )
            )
          )
        )
      )
    )
}
