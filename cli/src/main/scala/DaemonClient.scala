package cli

import pragma.utils.JsonCodec._, pragma.daemonProtocol._
import spray.json._
import requests._
import cats.implicits._
import scala.util._

object DaemonClient {

  val daemonPort = 9584
  val daemonUri = s"http://localhost:$daemonPort"

  def createProject(project: ProjectInput, mode: RunMode): Try[Unit] = {
    val modeStr = mode match {
      case Dev => "dev"
      case Prod => "prod"
    }

    pingLocalDaemon() *> Try {
      post(
        url = s"$daemonUri/project/create/$modeStr",
        data = project.toJson.compactPrint,
        keepAlive = false,
        check = false
      )
    }
  }

  def migrate(
      migration: MigrationInput,
      projectName: String,
      mode: RunMode
  ): Try[Unit] =
    pingLocalDaemon() *> Try {
      post(
        url = mode match {
          case Dev  => s"$daemonUri/project/migrate/dev/$projectName"
          case Prod => s"$daemonUri/project/migrate/prod/$projectName"
        },
        data = migration.toJson.compactPrint,
        chunkedUpload = true,
        check = true
      )
      println(s"Done migrating $projectName")
    }.void

  def pingLocalDaemon(numTrials: Int = 3): Try[Response] =
    Try(get(daemonUri + "/ping")).handleErrorWith { err =>
      if (numTrials <= 0) Failure {
        new Exception(
          s"Failed to reach Pragma daemon at $daemonUri. Please make sure the daemon is running and retry\n${err.getMessage}"
        )
      }
      else {
        Thread.sleep(2000)
        pingLocalDaemon(numTrials - 1)
      }
    }

}
