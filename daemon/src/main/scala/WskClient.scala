package running

import pragma.domain._
import cats.implicits._, cats.effect._
import org.http4s._, org.http4s.headers._, org.http4s.client._
import spray.json._
import running.utils.QueryError
import DaemonJsonProtocol._

class WskClient[M[_]: Sync](val config: WskConfig, val httpClient: Client[M]) {

  /**
    * Creates an OpenWhisk action
    *
    * @param actionName
    * @param actionCode Can be a base64-encoded string
    */
  def createAction(
      name: String,
      code: String,
      runtime: String,
      binary: Boolean,
      projectName: String
  ): M[Unit] = {
    val actionName = s"${projectName}_$name" // This is because wsk namespace don't work yet
    val namespace = "_"
    val reqBody = JsObject(
      "namespace" -> namespace.toJson,
      "name" -> actionName.toJson,
      "exec" -> JsObject(
        "kind" -> runtime.toJson,
        "code" -> code.toJson,
        "main" -> name.toJson,
        "binary" -> binary.toJson
      )
    )

    val wskApiVersion: Int = config.wskApiVersion

    val wskApiUri = config.wskApiHost / s"v$wskApiVersion"

    val endpoint =
      (wskApiUri / "namespaces" / namespace / "actions" / actionName)
        .withQueryParam(key = "overwrite", value = true)

    val `application/json` = MediaType.application.json

    val request = Request[M]()
      .withUri(endpoint)
      .withMethod(Method.PUT)
      .withHeaders(Authorization(config.wskAuthToken))
      .withContentType(`Content-Type`(`application/json`))
      .withBodyStream {
        fs2.Stream
          .fromIterator(reqBody.compactPrint.iterator)
          .map(_.toByte)
      }

    httpClient.expect[String](request).void
  }

  def invokeAction(
      function: ExternalFunction,
      args: JsValue,
      projectName: String
  ): M[JsValue] = {
    val wskApiVersion: Int = config.wskApiVersion

    val wskApiUri = config.wskApiHost / s"v$wskApiVersion"

    val namespace: String = "_"

    val actionName: String = s"${projectName}_${function.id}"

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
          implicitly[Sync[M]].raiseError[JsValue] {
            QueryError(
              s"Function ${function.id} result doesn't contain a `data` field"
            )
          }
      }
    } yield jsonResult
  }
}