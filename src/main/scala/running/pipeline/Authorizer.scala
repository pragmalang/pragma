package running.pipeline.functions

import running.pipeline._
import domain._
import domain.utils.InternalException
import domain.utils.AuthorizationError
import spray.json._
import setup.storage.Storage
import running.pipeline.Operation.ReadOperation
import akka.stream.scaladsl.Source
import sangria.ast._
import running.JwtPaylod
import domain.utils.UserError

case class Authorizer(
    syntaxTree: SyntaxTree,
    storage: Storage,
    devModeOn: Boolean = true
) {
  import Authorizer._

  def apply(request: Request): Source[(List[AuthorizationError], Request), _] =
    (syntaxTree.permissions, request.user) match {
      case (None, _) =>
        Source.failed(
          UserError.fromAuthErrors(AuthorizationError("Access Denied") :: Nil)
        )
      case (Some(permissions), None) => {
        val reqOps = Operation.operationsFrom(request)(syntaxTree)
        opsPass(reqOps.values.flatten.toVector, JsNull, devModeOn) match {
          case Right(true) =>
            Source.fromIterator(() => Iterator((Nil, request)))
          case Right(false) =>
            Source.failed(
              UserError
                .fromAuthErrors(AuthorizationError("Access Denied") :: Nil)
            )
          case Left(errors) =>
            Source.failed(UserError.fromAuthErrors(errors.toList))
        }
      }
      case (Some(permissions), Some(jwt)) => {
        val userModel = syntaxTree.models.find(_.id == jwt.role)

        if (!userModel.isDefined)
          return Source.failed(
            InternalException(
              s"Request has role `${jwt.role}` that doesn't exist"
            )
          )
        val user = storage.run(
          userQuery(userModel.get, jwt.userId),
          Vector(userReadOperation(userModel.get, jwt))
        )

        user.map { userJson =>
          val reqOps = Operation.operationsFrom(request)(syntaxTree)
          opsPass(reqOps.values.flatten.toVector, userJson, devModeOn) match {
            case Right(true) => (Nil, request)
            case Right(false) =>
              throw AuthorizationError("Unauthorized request")
            case Left(errors) => throw UserError.fromAuthErrors(errors.toList)
          }
        }
      }
    }

}
object Authorizer {
  def opsPass(
      ops: Vector[Operation],
      user: JsValue,
      devModeOn: Boolean = false // For error generation
  ): Either[Vector[AuthorizationError], Boolean] = {
    val results = for {
      op <- ops
      (allows, denies) = op.authRules.partition(_.ruleKind == Allow)
      denyExists = denies.exists(!opPasses(_, op, user))
      allowExists = allows.exists(opPasses(_, op, user))
    } yield (denyExists, allowExists, denies, allows)

    val allOpsAllowed = results.forall {
      case (denyExists, allowExists, _, _) => !denyExists && allowExists
    }

    if (!devModeOn) Right(allOpsAllowed)
    else {
      val errors = results.collect {
        case (false, true, _, _) => Nil
        case (true, _, denies, _) =>
          denies
            .filter(deny => ops.exists(!opPasses(deny, _, user)))
            .map { deny =>
              AuthorizationError(
                s"Request denied because of rule `$deny`",
                suggestion = Some(
                  "If you think that this request should be authorized, try removing the `deny` rule"
                ),
                position = deny.position
              )
            }
            .toVector
        case (false, false, _, allows) =>
          ops
            .filterNot(op => allows.exists(opPasses(_, op, user)))
            .map { op =>
              val resourcePath = Operation.displayOpResource(op)
              AuthorizationError(
                "Access Denied",
                cause = Some(
                  s"No `allow` rule exists to authorize ${op.opKind} on $resourcePath"
                ),
                suggestion = Some(
                  s"Try adding `allow ${op.event} $resourcePath` to your access rules for this request to be authorized"
                )
              )
            }
      }.flatten
      if (errors.isEmpty) Right(true)
      else Left(errors)
    }

  }

  def opPasses(rule: AccessRule, op: Operation, user: JsValue): Boolean = {
    val result = (op.targetModel == rule.resourcePath._1) &&
      ((op.fieldPath.headOption, rule.resourcePath._2) match {
        case (Some(opField), Some(ruleField)) =>
          opField.field == ruleField
        case _ => false
      }) &&
      (rule.predicate match {
        case None => true
        case Some(predicate) =>
          predicate
            .execute(user)
            .map {
              case JsTrue => true
              case _      => false
            }
            .getOrElse(false)
      })

    rule.ruleKind match {
      case Allow => result
      case Deny  => !result
    }
  }

  def userReadOperation(role: PModel, jwt: JwtPaylod) =
    Operation(
      ReadOperation,
      sangria.ast.OperationType.Query,
      Vector(Argument(role.primaryField.id, StringValue(jwt.userId))),
      Vector.empty,
      Vector.empty,
      Read,
      role,
      Nil,
      Some(role),
      Some(jwt),
      Nil,
      Nil
    )

  // Construct the query to get the user to be authorized from storage
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
