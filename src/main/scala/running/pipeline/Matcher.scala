package running.pipeline
import scala.util.Try
trait Matcher extends PiplineFunction[Request, Try[Request]] {
  override def apply(input: Request): Try[Request]
}

object CreateMatcher extends Matcher {
  override def apply(input: Request): Try[Request] = ???
}

object UpdateMatcher extends Matcher {
  override def apply(input: Request): Try[Request] = ???
}

object DeleteMatcher extends Matcher {
  override def apply(input: Request): Try[Request] = ???
}

object ReadMatcher extends Matcher {
  override def apply(input: Request): Try[Request] = ???
}