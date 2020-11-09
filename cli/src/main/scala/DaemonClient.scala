package cli

import pragma.daemonProtocol._, DaemonJsonProtocol._
import spray.json._
import requests._
import cats.implicits._
import scala.util._

object DaemonClient {

  val daemonPort = 9584
  val daemonUri = s"http://localhost:$daemonPort"

  def createProject(project: ProjectInput): Try[Unit] =
    ping *> Try {
      post(
        url = s"$daemonUri/project/create",
        data = project.toJson.compactPrint,
        keepAlive = false,
        check = false
      )
    }

  def devMigrate(migration: MigrationInput, projectName: String): Try[Unit] =
    ping *> Try {
      post(
        url = s"$daemonUri/project/migrate/dev/$projectName",
        data = migration.toJson.compactPrint,
        chunkedUpload = true,
        check = true
      )
      println(s"Done migrating $projectName")
    }.void

  def ping = Try(get(daemonUri + "/ping")).handleErrorWith { err =>
    Failure {
      new Exception(
        s"Failed to reach Pragma daemon at $daemonUri. Please make sure the daemon is running and retry\n${err.getMessage}"
      )
    }
  }

}
