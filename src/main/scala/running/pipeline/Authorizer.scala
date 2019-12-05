package running.pipeline
import sangria.ast.Document
import scala.util.Try
import domain.SyntaxTree

case class Authorizer(syntaxTree: SyntaxTree)
    extends PiplineFunction[Request, Try[Request]] {
  def apply(input: Request): Try[Request] = ???
}
