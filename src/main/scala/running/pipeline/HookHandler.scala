package running.pipeline
import sangria.ast.Document
import scala.util.Try
import domain.SyntaxTree
import domain.primitives.ExternalFunction

trait HookHandler[I <: PipelineInput, O <: Try[PipelineOutput]]
    extends PiplineFunction[I, O] {
  val syntaxTree: SyntaxTree
  def findHooks(request: Request): List[ExternalFunction] = ???
}

case class SetHookHandler(syntaxTree: SyntaxTree)
    extends HookHandler[Request, Try[Request]] {
  def apply(input: Request): Try[Request] = ???
}

case class GetHookHandler(syntaxTree: SyntaxTree)
    extends HookHandler[Request, Try[Response]] {
  def apply(input: Request): Try[Response] = ???
}

case class ValidateHookHandler(syntaxTree: SyntaxTree)
    extends HookHandler[Request, Try[Request]] {
  def apply(input: Request): Try[Request] = ???
}
