package running

import pragma.domain._
import cats.implicits._
import cats.effect.Async
import spray.json._
import org.http4s.client.blaze._
import org.http4s.client._
import scala.concurrent.ExecutionContext.global
import cats.effect.ConcurrentEffect
import cats.Monad
import org.http4s._, EntityDecoder.byteArrayDecoder
import org.http4s.implicits._
import org.http4s.headers._
import org.http4s.MediaType

object PFunctionExecutor {
  def execute[M[_]: Monad: Async: ConcurrentEffect](
      function: PFunctionValue,
      argList: List[JsValue]
  ): M[JsValue] = {
    val clientResource = BlazeClientBuilder[M](global).resource

    clientResource.use { client =>
      val namespace: String = ???
      val actionName: String = ???
      val wskApiVersion: Int = ???

      val wskApiUri = uri"https://localhost:2345/api" / s"v$wskApiVersion"

      val actionEndpoint =
        (wskApiUri / "namespaces" / namespace / "actions" / actionName)
          .withQueryParam(key = "blocking", value = true)
          .withQueryParam(key = "result", value = true)

      val actionArgs = JsObject.empty

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
        jsonResult = stringResult.parseJson
      } yield jsonResult
    }
  }
}
