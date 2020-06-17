package running.pipeline

import cats._
import cats.implicits._
import running.pipeline.functions._
import domain._
import setup.storage.Storage
import scala.util._
import domain.utils.UserError
import spray.json.JsObject

class RequestHandler[S, M[_]: Monad](
    syntaxTree: SyntaxTree,
    storage: Storage[S, M]
) {
  val reqValidator = new RequestValidator(syntaxTree)
  val reqReducer = new RequestReducer(syntaxTree)
  val authorizer = new Authorizer[S, M](syntaxTree, storage)

  def handle(req: Request): M[Either[Throwable, JsObject]] = {
    val reqOps = reqValidator(reqReducer(req))
      .map(Operations.from(_)(syntaxTree))
      .toEither

    type OpsOrError = Either[Throwable, Operations.OperationsMap]
    val authResult: M[OpsOrError] =
      reqOps
        .traverse(authorizer(_, req.user))
        .flatMap {
          case Left(err) =>
            Left(err).pure[M].widen[OpsOrError]
          case Right(Left(errors)) if errors.isEmpty => reqOps.pure[M]
          case Right(Left(errors)) =>
            Left(UserError.fromAuthErrors(errors)).pure[M].widen[OpsOrError]
          case Right(Right(true)) => reqOps.pure[M]
          case _ =>
            Left(UserError("Unauthorized access")).pure[M].widen[OpsOrError]
        }

    // Add hook execution here

    val storageResult =
      authResult.flatMap(ops => ops.traverse(storage.run(_)))

    storageResult // Return hook results instead
  }

}
