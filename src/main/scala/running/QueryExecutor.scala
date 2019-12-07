package running
import domain.SyntaxTree
import sangria.ast.Document
import setup.storage.Storage
import scala.util.Try
import scala.util.Failure
import scala.util.Success
import running.pipeline._
import Implicits._
import spray.json._
import spray.json.DefaultJsonProtocol._

case class QueryExecutor(
    syntaxTree: SyntaxTree,
    storage: Storage,
    resolvers: List[QueryResolver] = QueryExecutor.resolvers
) {
  def execute(request: Request): Try[Response] = Try {
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
        _.resolver(
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
  }
}

object QueryExecutor {
  val resolvers = List(
    CreateResolver
  )
}

trait QueryResolver {
  val matcher: Matcher
  def resolver(
      request: Request,
      syntaxTree: SyntaxTree,
      resultTransformer: Response => Response
  ): Response
}

object CreateResolver extends QueryResolver {
  override val matcher: Matcher = CreateMatcher
  override def resolver(
      request: Request,
      syntaxTree: SyntaxTree,
      resultTransformer: Response => Response
  ): Response = BaseResponse(200, JsNull)
}
