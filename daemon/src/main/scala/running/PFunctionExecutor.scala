package running

import pragma.domain._
import cats.implicits._, cats.effect._
import org.http4s._
import spray.json._
import pragma.domain.utils.InternalException
import org.http4s.client.blaze.BlazeClientBuilder
import scala.concurrent.ExecutionContext
import running.RunningImplicits._
import pragma.utils.JsonCodec._
import cats.Monad
import org.http4s

class PFunctionExecutor[M[_]: ConcurrentEffect](metanodeUri: Uri) {
  def execute(
      function: PFunctionValue,
      args: List[JsValue]
  ): M[JsValue] =
    function match {
      case function: ExternalFunction => {
        val httpClient = BlazeClientBuilder[M](ExecutionContext.global).resource
        val response = httpClient.use { client =>
          val uri =
            metanodeUri / "call" / function.scopeName / function.id
          val body = JsObject("params" -> JsArray(args.toVector)).compactPrint
          val req = http4s
            .Request[M]()
            .withMethod(Method.POST)
            .withUri(uri)
            .withBodyStream(fs2.Stream.emits(body.getBytes))
            .withHeaders(
              Header("Accept", "*/*"),
              Header("Content-Type", "application/json")
            )

          client
            .fetchAs[JsValue](req)
        }

        response
      }
      case other =>
        InternalException(
          s"Unhandled function type `${other.getClass().getCanonicalName()}`"
        ).raiseError[M, JsValue]
    }

  def load(path: String, scopeName: String, resolutionPath: String) = {
    val httpClient = BlazeClientBuilder[M](ExecutionContext.global).resource
    val filePath = os.Path(resolutionPath) / os.RelPath(path)
    httpClient
      .flatMap { client =>
        val uri = metanodeUri / "load" / filePath.baseName / scopeName
        val parent = os.Path(filePath.wrapped.getParent())
        val body: JsValue = JsObject("resolution_path" -> parent.toString.toJson)
        val req = http4s
          .Request[M](Method.POST, uri = uri)
          .withEntity(body)
          .withHeaders(
            Header("Accept", "*/*"),
            Header("Content-Type", "application/json")
          )
        client.run(req)
      }
      .use(_ => Monad[M].pure(()))
  }
}

object PFunctionExecutor {
  import cats.effect.Blocker
  import java.util.concurrent._

  val blockingPool = Executors.newFixedThreadPool(5)
  val blocker = Blocker.liftExecutorService(blockingPool)
  def dummy[M[_]: ConcurrentEffect: ContextShift](metanodeUri: Uri) =
    new PFunctionExecutor[M](metanodeUri) {
      override def execute(
          function: PFunctionValue,
          args: List[JsValue]
      ): M[JsValue] = JsObject.empty.pure[M].widen[JsValue]
    }

  case object InvalidJsonException extends Exception("Value cannot be converted to JSON")
}

case class WskConfig(
    wskApiVersion: Int,
    wskApiUrl: Uri,
    wskAuthToken: BasicCredentials
)
