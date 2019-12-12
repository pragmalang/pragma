package running.execution

import domain.SyntaxTree
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

case class QueryExecutor(
    syntaxTree: SyntaxTree,
    storage: Storage,
    requestHandlers: List[RequestHandler] = RequestHandler.defaultHandlers
) {

  val authorizer = Authorizer(syntaxTree)
  val requestValidator = RequestValidator(syntaxTree)
  val requestReducer = RequestReducer(syntaxTree)
  val validateHookHandler = ValidateHookHandler(syntaxTree)
  val setHookHandler = SetHookHandler(syntaxTree)
  val getHookHandler = GetHookHandler(syntaxTree)

  def execute(request: Request): Try[Response] = Try {
    val authorizationResult = authorizer(request).get
    val requestValidationResult = requestValidator(authorizationResult).get
    val reducedRequest = requestReducer(requestValidationResult).get
    val validateHookResult = validateHookHandler(reducedRequest).get
    val setHookResult = setHookHandler(validateHookResult).get
    requestHandlers
      .find(_.matcher(setHookResult))
      .map(
        _.handler(
          request = setHookResult,
          syntaxTree = syntaxTree,
          responseTransformer = response => {
            val getHookResult = getHookHandler {
              request.copy(data = Some(response.body))
            }
            getHookResult.get
          }
        )
      )
      .get
  }

}
