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
import scala.util._
import cats.Monad
import cats.implicits._

class Authorizer[S, M[_]: Monad](
    syntaxTree: SyntaxTree,
    storage: Storage[S, M],
    devModeOn: Boolean = true
) {
  import Authorizer._

  def apply(request: Request): M[AuthorizationResult] =
    request.user match {
      case None => {
        val reqOps = Operations.from(request)(syntaxTree)
        Monad[M].pure(results(reqOps.values.flatten.toVector, JsNull))
      }
      case Some(jwt) => {
        val userModel = syntaxTree.modelsById.get(jwt.role) match {
          case Some(model) => model
          case _ =>
            return Monad[M].pure(
              Left(
                Vector(
                  AuthorizationError(
                    s"Request has role `${jwt.role}` that doesn't exist"
                  )
                )
              )
            )
        }

        val user = storage
          .run(
            Map(None -> Vector(userReadOperation(userModel, jwt)))
          )

        user.map {
          case userJson: JsObject
              if userJson.fields.get(userModel.primaryField.id) ==
                Some(JsString(jwt.userId)) => {
            val reqOps = Operations.from(request)(syntaxTree)
            results(reqOps.values.flatten.toVector, userJson)
          }
          case _ =>
            throw InternalException(
              "User retrieved from storage must be an object with the same ID as the request's JWT"
            )
        }
      }
    }

  def results(
      ops: Vector[Operation],
      predicateArg: JsValue
  ): AuthorizationResult =
    ops.map(opResults(_, predicateArg)).reduce(combineResults)

  def opResults(op: Operation, predicateArg: JsValue): AuthorizationResult = {
    val rules = relevantRules(op)

    val acc: AuthorizationResult =
      if (devModeOn) Left(Vector.empty)
      else Right(true)
    val argsResult =
      rules
        .map(opArgumentsResult(_, op, predicateArg))
        .foldLeft(acc)(combineResults)

    val innerOpResults = innerReadResults(op, predicateArg)

    println(
      s"Operation ${op.event} on ${op.targetModel.id} by ${op.user}: \n- Arg results: ${argsResult}\n- Inner read results: ${innerOpResults}"
    )

    combineResults(argsResult, innerOpResults)
  }

  /** Returns all the rules that can match */
  def relevantRules(op: Operation): Seq[AccessRule] =
    syntaxTree.permissions.rulesOf(op.role, op.targetModel, op.event)

  /** Note: should only recieve a relevant rule (use `relevantRules`) */
  def opArgumentsResult(
      rule: AccessRule,
      op: Operation,
      predicateArg: JsValue
  ): AuthorizationResult = {
    lazy val argsMatchRule = (rule.resourcePath._2, op.event) match {
      case (None, _) => true
      case (Some(ruleField), Update) =>
        op.opArguments
          .find(_.name == op.targetModel.id.small)
          .map(_.value) match {
          case Some(ObjectValue(fields, _, _)) => {
            fields.exists(_.name == ruleField.id)
          }
          case _ =>
            throw InternalException(
              "Argument passed to `update` is not an object. Something must've went wrond duing query validation or schema generation"
            )
        }
      case (Some(ruleField), UpdateMany) => {
        op.opArguments
          .find(_.name == "items")
          .map(_.value) match {
          case Some(ListValue(values, _, _)) =>
            values.exists { value =>
              value.isInstanceOf[ObjectValue] &&
              value
                .asInstanceOf[ObjectValue]
                .fields
                .exists(_.name == ruleField.id)
            }
          case _ =>
            throw InternalException(
              "`items` argument must be passed to `updateMany` and it must be a list of values"
            )
        }
      }
      case (Some(ruleField), Create)
          if rule.permissions.contains(SetOnCreate) => {
        op.opArguments
          .find(_.name == op.targetModel.id.small)
          .map(_.value) match {
          case Some(ObjectValue(fields, _, _)) =>
            fields.exists(_.name == ruleField.id)
          case _ =>
            throw InternalException(
              s"Argument `${op.targetModel.id.small}` must be passed to `CREATE` and it must be an object"
            )
        }
      }
      case _ => false
    }
    println(
      "Args of " + op.event + " on " + op.targetModel.id + " match: " + argsMatchRule
    )

    rule.ruleKind match {
      case Allow if argsMatchRule => userPredicateResult(rule, predicateArg)
      case Allow if devModeOn     => Left(Vector.empty)
      case Allow                  => Right(true)
      case Deny if !devModeOn && argsMatchRule =>
        userPredicateResult(rule, predicateArg, negate = true)
      case Deny if devModeOn && argsMatchRule => {
        val error = op.event match {
          case Create if rule.permissions.contains(SetOnCreate) =>
            AuthorizationError("Denied setting attribute in `CREATE` operation")
          case Update | UpdateMany =>
            AuthorizationError(
              "Denied updating attribute in `UPDATE` operation"
            )
          case _ => AuthorizationError("Denied operation arguments")
        }
        combineResults(
          userPredicateResult(rule, predicateArg, negate = true),
          Left(Vector(error))
        )
      }
    }
  }

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
            case Right(value)                   => value
            case Left(errors) if errors.isEmpty => true
            case _                              => false
          })
        }
        denyExists = denies.exists { deny =>
          relevantRuleMatchesInnerReadOp(deny, innerOp) &&
          (userPredicateResult(deny, predicateArg) match {
            case Right(value)                   => value
            case Left(errors) if errors.isEmpty => true
            case _                              => false
          })
        }
      } yield (innerOp, allowExists, denyExists)

      lazy val opIsAllowed = results.forall {
        case (_, allowExists, denyExists) => allowExists && !denyExists
      }

      lazy val errors = results.collect {
        case (innerOp, false, false) =>
          AuthorizationError(
            s"No `allow` rule exists to allow `${innerOp.operation.event}` operations on `${innerOp.displayTargetResource}`"
          )
        case (innerOp, _, true) =>
          AuthorizationError(
            s"`deny` rule exists that prohibits `${innerOp.operation.event}` operations on `${innerOp.displayTargetResource}`",
            Some(
              "Try removing this rule if you would like this operation to be allowed"
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

  /**
    * Returns the boolean result of the user
    * predicate or the error thrown by the user
    */
  def userPredicateResult(
      rule: AccessRule,
      argument: JsValue,
      negate: Boolean = false
  ): AuthorizationResult =
    rule.predicate match {
      case None if !devModeOn => Right(true)
      case None               => Left(Vector.empty)
      case Some(predicate) => {
        val predicateResult = predicate
          .execute(argument)
          .map {
            case JsTrue if negate  => false
            case JsTrue            => true
            case JsFalse if negate => true
            case JsFalse           => false
          }
        predicateResult match {
          case Success(value) if !devModeOn => Right(value)
          case Failure(_) if !devModeOn     => Right(false)
          case Success(false) if negate =>
            Left(Vector(AuthorizationError("Predicate denies access")))
          case Success(true) if negate && devModeOn => Left(Vector.empty)
          case Success(_)                           => Left(Vector.empty)
          case Failure(err) =>
            Left(Vector(AuthorizationError(err.getMessage())))
        }
      }
    }

}
object Authorizer {

  type AuthorizationResult = Either[Vector[AuthorizationError], Boolean]

  def combineResults(r1: AuthorizationResult, r2: AuthorizationResult) =
    (r1, r2) match {
      case (Right(b1), Right(b2)) => Right(b1 && b2)
      case (Left(e1), Left(e2))   => Left(e1.concat(e2).distinctBy(_.message))
      case _                      => Right(false)
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
