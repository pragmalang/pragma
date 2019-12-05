package running.pipeline
import sangria.ast.Document
import scala.util.Try
import domain.SyntaxTree
import domain.primitives.ExternalFunction

trait HookHandler {
  val syntaxTree: SyntaxTree
  def findHooks(request: Request): List[ExternalFunction] = ???
}

case class SetHookHandler(syntaxTree: SyntaxTree)
    extends PiplineFunction[Request, Try[Request]] {
  def apply(input: Request): Try[Request] = ???
}

case class GetHookHandler(syntaxTree: SyntaxTree)
    extends PiplineFunction[Request, Try[Response]] {
  def apply(input: Request): Try[Response] = ???
}

case class ValidateHookHandler(syntaxTree: SyntaxTree)
    extends PiplineFunction[Request, Try[Request]] {
  def apply(input: Request): Try[Request] = ???
}
