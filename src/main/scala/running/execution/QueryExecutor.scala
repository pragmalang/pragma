package running.execution

import domain.{SyntaxTree, utils}, utils.userErrorFrom
import setup.storage.Storage
import setup.schemaGenerator.ApiSchemaGenerator
import running.pipeline._, functions._
import running.Implicits._

import sangria.ast._
import sangria.execution._
import sangria.schema._
import spray.json._
import spray.json.DefaultJsonProtocol._

import scala.util.{Try, Success, Failure}
import sangria.schema.AstSchemaBuilder
import sangria.execution.deferred.DeferredResolver
import akka.stream.scaladsl.Source
import domain.utils.`package`.UserError

case class QueryExecutor(
    syntaxTree: SyntaxTree,
    storage: Storage
) {
  val authorizer = Authorizer(syntaxTree)
  val requestValidator = RequestValidator(syntaxTree)
  val requestReducer = RequestReducer(syntaxTree)
  val validateHookHandler = ValidateHookHandler(syntaxTree)
  val setHookHandler = SetHookHandler(syntaxTree)
  val getHookHandler = GetHookHandler(syntaxTree)

  def responseTransformer(request: Request)(response: Response) =
    getHookHandler {
      request.copy(data = Some(response.body))
    }

  def execute(request: Request): Try[Either[Response, Source[Response, _]]] =
    authorizer(request)
      .flatMap(requestValidator.apply)
      .flatMap(requestReducer.apply)
      .flatMap(validateHookHandler.apply)
      .flatMap(setHookHandler.apply)
      .map { request =>
        RequestHandler.handle(
          request = request,
          syntaxTree = syntaxTree,
          responseTransformer = response =>
            responseTransformer(request)(response) match {
              case Failure(exception) => throw exception
              case Success(value)     => value
            }
        )
      }
}
