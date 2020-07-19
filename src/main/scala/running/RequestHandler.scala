package running

import domain._, domain.utils.UserError
import storage.Storage
import cats._
import cats.implicits._
import scala.util._
import spray.json._

class RequestHandler[S, M[_]: Monad](
    syntaxTree: SyntaxTree,
    storage: Storage[S, M]
) {
  val reqValidator = new RequestValidator(syntaxTree)
  val authorizer = new Authorizer[S, M](syntaxTree, storage)

  def handle(req: Request): M[Either[Throwable, JsObject]] = {
    val authResult = for {
      validationResult <- reqValidator(RequestReducer(req)).toEither
      ops <- Operations.from(validationResult)(syntaxTree)
      result = authorizer(ops, req.user)
    } yield
      result map {
        case Left(errors) if !errors.isEmpty => throw errors.head
        case Left(_)                         => ops
        case Right(passed) =>
          if (passed) ops
          else throw UserError("Unauthorized access")
      }

    // Add hook execution here
    // Return hook results instead

    val storageResult =
      authResult.traverse(ops => ops.flatMap(storage.run))

    storageResult.map { result =>
      result
        .map(
          data => JsObject(Map("data" -> data, "errors" -> JsArray.empty))
        )
        .recover {
          case err: Throwable =>
            JsObject(Map("errors" -> JsArray(Vector(JsString(err.getMessage)))))
        }
    }

  }

}
