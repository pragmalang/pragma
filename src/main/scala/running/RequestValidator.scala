package running

import scala.util.Try
import domain.SyntaxTree
import sangria.validation.QueryValidator
import sangria.schema.Schema
import setup.schemaGenerator.ApiSchemaGenerator
import running.utils.QueryError

class RequestValidator(syntaxTree: SyntaxTree) {

  def apply(input: Request): Try[Request] =
    validateQuery(input)

  val queryValidator = QueryValidator.default

  val apiSchemaGenerator = ApiSchemaGenerator(syntaxTree)

  val sangriaSchema =
    Schema.buildFromAst(apiSchemaGenerator.buildApiSchemaAsDocument)

  def validateQuery(request: Request): Try[Request] = Try {
    val violations = queryValidator
      .validateQuery(sangriaSchema, request.query)

    if (!violations.isEmpty)
      throw new QueryError(violations.map(_.errorMessage))

    request
  }
}
