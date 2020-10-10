package running

import pragma.domain._
import running.utils.QueryError
import cats.implicits._, cats.effect._
import org.http4s._, org.http4s.headers._, org.http4s.client._
import spray.json._

class PFunctionExecutor[M[_]: Sync](
    config: WskConfig,
    projectName: String,
    httpClient: Client[M]
) {
  def execute(
      function: PFunctionValue,
      args: JsValue
  ): M[JsValue] = {
    val wskApiVersion: Int = config.wskApiVersion

    val wskApiUri = config.wskApiHost / s"v$wskApiVersion"

    val namespace: String = projectName

    val actionName: String = function.id

    val actionEndpoint =
      (wskApiUri / "namespaces" / namespace / "actions" / actionName)
        .withQueryParam(key = "blocking", value = true)
        .withQueryParam(key = "result", value = true)

    val `application/json` = MediaType.application.json

    val actionArgs = JsObject(
      "data" -> args
    )

    val request = Request[M]()
      .withUri(actionEndpoint)
      .withMethod(Method.POST)
      .withHeaders(Authorization(config.wskAuthToken))
      .withContentType(`Content-Type`(`application/json`))
      .withBodyStream {
        fs2.Stream
          .fromIterator(actionArgs.compactPrint.iterator)
          .map(_.toByte)
      }

    for {
      stringResult <- httpClient.expect[String](request)
      jsonResult <- stringResult.parseJson.asJsObject.fields
        .get("data") match {
        case Some(value) => value.pure[M]
        case None =>
          implicitly[Sync[M]].raiseError[JsValue](QueryError(""))
      }
    } yield jsonResult
  }
}
object PFunctionExecutor {
  import cats.effect.Blocker
  import java.util.concurrent._

  val blockingPool = Executors.newFixedThreadPool(5)
  val blocker = Blocker.liftExecutorService(blockingPool)
  def dummy[M[_]: ConcurrentEffect: ContextShift] =
    new PFunctionExecutor[M](
      WskConfig(
        1,
        Uri.fromString("http://localhost:6000").toTry.get,
        BasicCredentials("DUMMY", "DUMMY")
      ),
      "<DUMMY PROJECT>",
      JavaNetClientBuilder[M](blocker).create
    ) {
      override def execute(
          function: PFunctionValue,
          args: JsValue
      ): M[JsValue] = JsNull.pure[M].widen[JsValue]
    }
}

case class WskConfig(
    wskApiVersion: Int,
    wskApiHost: Uri,
    wskAuthToken: BasicCredentials
)
