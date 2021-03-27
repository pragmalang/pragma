package running

import pragma.domain._
import cats._, cats.implicits._, cats.effect._
import org.http4s._
import spray.json._
import pragma.domain.utils.InternalException
import metacall._
import scala.concurrent.Future
import running.utils._

class PFunctionExecutor[M[_]: Sync](implicit fromFuture: Future ~> M) {
  def execute(
      function: PFunctionValue,
      args: List[JsValue]
  ): M[JsValue] = function match {
    case function: ExternalFunction =>
      fromFuture(
        Caller.callV(function.id, args.map(toMetacallValue), function.scopeName.some)
      ).flatMap(PFunctionExecutor.toJsonM(_)(implicitly[MonadError[M, Throwable]]))
    case other =>
      InternalException(
        s"Unhandled function type `${other.getClass().getCanonicalName()}`"
      ).raiseError[M, JsValue]
  }
}

object PFunctionExecutor {
  import cats.effect.Blocker
  import java.util.concurrent._

  val blockingPool = Executors.newFixedThreadPool(5)
  val blocker = Blocker.liftExecutorService(blockingPool)
  def dummy[M[_]: ConcurrentEffect: ContextShift](implicit fromFuture: scala.concurrent.Future ~> M) =
    new PFunctionExecutor[M] {
      override def execute(
          function: PFunctionValue,
          args: List[JsValue]
      ): M[JsValue] = JsObject.empty.pure[M].widen[JsValue]
    }

  private def toJsonM[M[_]](
      v: metacall.Value
  )(implicit ME: ApplicativeError[M, Throwable]): M[JsValue] =
    fromMetacallValue(v) match {
      case Some(v) => ME.pure(v)
      case None    => ME.raiseError(InvalidJsonException)
    }

  case object InvalidJsonException extends Exception("Value cannot be converted to JSON")
}

case class WskConfig(
    wskApiVersion: Int,
    wskApiUrl: Uri,
    wskAuthToken: BasicCredentials
)
