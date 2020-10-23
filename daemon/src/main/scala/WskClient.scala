package running

import pragma.domain._, pragma.daemonProtocol.DaemonJsonProtocol._
import cats.implicits._, cats.effect._
import org.http4s._, org.http4s.headers._, org.http4s.client._
import spray.json._
import scala.util.{Success, Try}
import cats.MonadError

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
      projectName: String,
      scopeName: String
  ): M[Unit] = {
    val actionName = s"${projectName}_${scopeName}_$name" // This is because wsk namespace don't work yet
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

    val wskApiUrl = config.wskApiUrl / "api" / s"v$wskApiVersion"

    val endpoint =
      (wskApiUrl / "namespaces" / namespace / "actions" / actionName)
        .withQueryParam(key = "overwrite", value = true)

    val bodyBytes = reqBody.compactPrint.getBytes()

    val request = Request[M](
      method = Method.PUT,
      uri = endpoint,
      headers = Headers.of(
        Authorization(config.wskAuthToken),
        Header("Accept", "application/json"),
        Header("Content-Type", "application/json"),
        Header("Content-Length", bodyBytes.length.toString)
      ),
      body = fs2.Stream.emits(bodyBytes)
    )

    httpClient.expectOr(request) { res =>
      res.bodyText.compile.string.map { body =>
        new Exception(
          s"Failed to create OpenWhisk action `$name`. OpenWhisk response: ${body.toJson.prettyPrint}"
        )
      }
    }
  }

  def invokeAction(
      function: ExternalFunction,
      args: JsObject,
      projectName: String
  ): M[JsObject] = {
    val wskApiVersion: Int = config.wskApiVersion

    val wskApiUri = config.wskApiUrl / "api" / s"v$wskApiVersion"

    val namespace = "_"

    val actionName =
      s"${projectName}_${function.scopeName}_${function.id}"

    val actionEndpoint =
      (wskApiUri / "namespaces" / namespace / "actions" / actionName)
        .withQueryParam(key = "blocking", value = true)
        .withQueryParam(key = "result", value = true)

    val actionArgs = args match {
      case obj: JsObject => obj
      case args =>
        JsObject(
          "data" -> args
        )
    }

    val bodyBytes =
      actionArgs.compactPrint.getBytes(java.nio.charset.StandardCharsets.UTF_8)

    val request = Request[M](
      uri = actionEndpoint,
      method = Method.POST,
      headers = Headers.of(
        Authorization(config.wskAuthToken),
        Header("Content-Type", "application/json"),
        Header("Accept", "application/json"),
        Header("Content-Length", bodyBytes.length.toString)
      ),
      body = fs2.Stream.emits(bodyBytes)
    )

    val responseBodyString = httpClient.expectOr[String](request) { res =>
      res.bodyText.compile.string.flatMap { body =>
        MonadError[M, Throwable].raiseError {
          new Exception(
            s"Request to OpenWhisk for invoking function `${function.scopeName}.${function.id}` failed with HTTP status code ${res.status.code}:\n$body"
          )
        }
      }
    }

    for {
      stringResult <- responseBodyString
      jsonResult <- Try(stringResult.parseJson) match {
        case Success(obj: JsObject) => obj.pure[M]
        case Success(invalid) =>
          MonadError[M, Throwable].raiseError {
            new Exception(
              s"Invalid value `${invalid.compactPrint}` returned by function `${function.id}` (the return value must be an object)"
            )
          }
        case _ =>
          MonadError[M, Throwable].raiseError {
            new Exception(
              s"Invalid JSON result returned by call to `${function.id}`"
            )
          }
      }
    } yield jsonResult
  }
}
