package running

import pragma.domain._
import cats.implicits._
import spray.json._
import org.http4s.client.blaze._
import scala.concurrent.ExecutionContext.global
import cats.effect.ConcurrentEffect
import org.http4s._, EntityDecoder.byteArrayDecoder
import org.http4s.headers._
import org.http4s.MediaType
import running.utils.QueryError

class PFunctionExecutor[M[_]: ConcurrentEffect](config: WskConfig) {
  private val clientResource = BlazeClientBuilder[M](global).resource

  def execute(
      function: PFunctionValue,
      args: JsValue
  ): M[JsValue] = {
    clientResource.use { client =>
      val wskApiVersion: Int = config.wskApiVersion

      val wskApiUri = config.wskApiHost / s"v$wskApiVersion"

      val namespace: String = config.projectId.toString()

      val actionName: String = function.id

      val actionEndpoint =
        (wskApiUri / "namespaces" / namespace / "actions" / actionName)
          .withQueryParam(key = "blocking", value = true)
          .withQueryParam(key = "result", value = true)

      val actionArgs = JsObject(
        "data" -> args
      )

      val request = Request[M]()
        .withUri(actionEndpoint)
        .withMethod(Method.POST)
        .withContentType(`Content-Type`(MediaType.application.json))
        .withBodyStream {
          fs2.Stream
            .fromIterator(actionArgs.compactPrint.iterator)
            .map(_.toByte)
        }

      for {
        bytes <- client.expect(request)(byteArrayDecoder)
        stringResult = bytes.toVector.map(_.toChar).foldLeft("")(_ + _)
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

case class WskConfig(wskApiVersion: Int, projectId: Int, wskApiHost: Uri)
