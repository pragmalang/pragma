package running

import pragma.domain._
import cats.implicits._, cats.effect._
import org.http4s._, org.http4s.client._
import spray.json._
import pragma.domain.utils.InternalException

class PFunctionExecutor[M[_]: Sync](
    projectName: String,
    wskClient: WskClient[M]
) {
  def execute(
      function: PFunctionValue,
      args: JsObject
  ): M[JsObject] = function match {
    case function: ExternalFunction =>
      wskClient.invokeAction(function, args, projectName)
    case other =>
      InternalException(
        s"Unhandled function type `${other.getClass().getCanonicalName()}`"
      ).raiseError[M, JsObject]
  }
}
object PFunctionExecutor {
  import cats.effect.Blocker
  import java.util.concurrent._

  val blockingPool = Executors.newFixedThreadPool(5)
  val blocker = Blocker.liftExecutorService(blockingPool)
  def dummy[M[_]: ConcurrentEffect: ContextShift] = {
    val dummyWskConfig = WskConfig(
      1,
      Uri.fromString("http://localhost:6000").toTry.get,
      BasicCredentials("DUMMY", "DUMMY")
    )
    new PFunctionExecutor[M](
      "<DUMMY PROJECT>",
      new WskClient[M](dummyWskConfig, JavaNetClientBuilder[M](blocker).create)
    ) {
      override def execute(
          function: PFunctionValue,
          args: JsObject
      ): M[JsObject] = JsObject.empty.pure[M]
    }
  }
}

case class WskConfig(
    wskApiVersion: Int,
    wskApiUrl: Uri,
    wskAuthToken: BasicCredentials
)
