package running.execution

import domain.SyntaxTree
import setup.storage.Storage
import setup.schemaGenerator.ApiSchemaGenerator
import running.pipeline._
import running.Implicits._

import sangria.ast._
import sangria.execution._
import sangria.schema._
import spray.json._
import spray.json.DefaultJsonProtocol._

import scala.util.{Try, Success, Failure}
import scala.concurrent.Future
import sangria.schema.AstSchemaBuilder

case class QueryExecutor(
    syntaxTree: SyntaxTree,
    storage: Storage,
    resolvers: List[RequestHandler] = RequestHandler.defaultHandlers
) {
  def execute(request: Request): Try[Future[Response]] = Try {
    val authorizer = Authorizer(syntaxTree)
    val authorizationResult = authorizer(request).get
    val validateHookHandler = ValidateHookHandler(syntaxTree)
    val validateHookResult = validateHookHandler(authorizationResult).get
    val setHookHandler = SetHookHandler(syntaxTree)
    val setHookResult = setHookHandler(validateHookResult).get
    val getHookHandler = GetHookHandler(syntaxTree)
    resolvers
      .find(_.matcher(setHookResult).isSuccess)
      .map(
        _.handler(
          setHookResult,
          syntaxTree,
          response => {
            val getHookResult = getHookHandler(
              request.copy(data = Some(response.body))
            )
            getHookResult match {
              case Failure(exception) =>
                HttpErrorResponse(
                  422,
                  JsObject("error" -> "Invalid Input".toJson)
                )
              case Success(value) => value
            }
          }
        )
      )
      .get
    ???
  }

  val schema =
    Schema.buildFromAst(
      ApiSchemaGenerator.default(syntaxTree).buildApiSchema,
      AstSchemaBuilder.resolverBased(GenericResolver.fieldResolver)
    )

  val genericResolver = GenericResolver(syntaxTree, storage)
  
  def execute(query: Document): Try[Future[Response]] = {
    import scala.concurrent.ExecutionContext.Implicits._
    val result = Executor
      .execute(
        schema,
        query,
        JsObject("a" -> "a".toJson),
        deferredResolver = genericResolver
      )
    ???
  }
}
