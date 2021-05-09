package cli

import pragma.utils.JsonCodec._
import spray.json._
import requests._
import cats.implicits._
import scala.util._
import pragma.RunMode

class DaemonClient(port: Int, hostname: String) {
  val daemonUri = s"http://${hostname}:$port"

  def migrate(
      code: String,
      resolutionPath: String,
      projectName: String,
      mode: RunMode
  ): Try[Unit] = {
    val data = JsObject(
      "code" -> code.toJson,
      "resolutionPath" -> resolutionPath.toJson
    ).compactPrint

    Try {
      post(
        url = mode match {
          case RunMode.Dev  => s"$daemonUri/project/migrate/dev/$projectName"
          case RunMode.Prod => s"$daemonUri/project/migrate/prod/$projectName"
        },
        data = data,
        chunkedUpload = true,
        check = true,
        headers = Map("Content-Length" -> data.getBytes().length.toString(), "Content-Type" -> "application/json")
      )
      println(s"Done migrating $projectName")
    }.void
  }

}
