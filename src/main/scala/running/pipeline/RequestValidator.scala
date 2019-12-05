package running.pipeline
import scala.util.Try

object RequestValidator extends PiplineFunction[Request, Try[Request]] {
  override def apply(input: Request): Try[Request] = ???
}
