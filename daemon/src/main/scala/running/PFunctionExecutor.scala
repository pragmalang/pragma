package running

import pragma.domain._
import cats.implicits._
import spray.json._
import org.http4s.client.blaze._
import scala.concurrent.ExecutionContext.global
import cats.effect.ConcurrentEffect
import org.http4s._
import org.http4s.headers._
import org.http4s.MediaType
import running.utils.QueryError

class PFunctionExecutor[M[_]: ConcurrentEffect](
    config: WskConfig,
    projectName: String
) {
  private val clientResource = BlazeClientBuilder[M](global).resource

  def execute(
      function: PFunctionValue,
      args: JsValue
  ): M[JsValue] = {
    clientResource.use { client =>
      val wskApiVersion: Int = config.wskApiVersion

      val wskApiUri = config.wskApiHost / s"v$wskApiVersion"

      val namespace: String = projectName

      val actionName: String = function.id

      val wskCredentials =
        BasicCredentials(config.wskAuthToken)

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
        .withHeaders(Authorization(wskCredentials))
        .withContentType(`Content-Type`(`application/json`))
        .withBodyStream {
          fs2.Stream
            .fromIterator(actionArgs.compactPrint.iterator)
            .map(_.toByte)
        }

      for {
        stringResult <- client.expect[String](request)
        jsonResult <- stringResult.parseJson.asJsObject.fields
          .get("data") match {
          case Some(value) => value.pure[M]
          case None =>
            implicitly[ConcurrentEffect[M]].raiseError[JsValue](QueryError(""))
        }
      } yield jsonResult
    }
  }
}
object PFunctionExecutor {
  def dummy[M[_]: ConcurrentEffect] =
    new PFunctionExecutor[M](
      WskConfig(
        1,
        Uri.fromString("http://localhost:6000").toTry.get,
        "2112ssdf"
      ),
      "<DUMMY PROJECT>"
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
    wskAuthToken: String
)
