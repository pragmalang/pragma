package cli

import pragma.daemonProtocol._, DaemonJsonProtocol._
import spray.json._
import requests._
import cats.implicits._
import scala.util._

object DaemonClient {

  val daemonUri = "http://localhost:3030"

  def createProject(project: ProjectInput): Try[Unit] =
    ping *> Try {
      post(s"$daemonUri/project/create", data = project.toJson.compactPrint)
    }.handleErrorWith {
      case err =>
        Failure(new Exception(s"Unable to create project\n${err.getMessage}"))
    }.void

  def devMigrate(migration: MigrationInput, projectName: String): Try[Unit] =
    ping *> Try {
      post(
        s"$daemonUri/project/migrate/dev/$projectName",
        data = migration.toJson.compactPrint
      )
    }.handleErrorWith {
      case err =>
        Failure {
          new Exception {
            s"Failed to migrate project $projectName\n${err.getMessage}"
          }
        }
    }.void

  def ping = Try(get(daemonUri)).handleErrorWith { err =>
    Failure {
      new Exception(s"Failed to connect to Pragma daemon\n${err.getMessage}")
    }
  }

}