package running.pipeline
import scala.util.Try
import domain.SyntaxTree
import sangria.ast.Document
import sangria.validation.Violation
import sangria.validation.QueryValidator
import sangria.schema.Schema
import setup.schemaGenerator.DefaultApiSchemaGenerator

object RequestValidator extends PiplineFunction[Request, Try[Request]] {
  override def apply(input: Request): Try[Request] = ???

  def validateQuery(
      syntaxTree: SyntaxTree,
      queryAst: Document
  ): List[Violation] =
    QueryValidator.default
      .validateQuery(
        Schema
          .buildFromAst(DefaultApiSchemaGenerator(syntaxTree).buildApiSchema),
        queryAst
      )
      .toList
}
