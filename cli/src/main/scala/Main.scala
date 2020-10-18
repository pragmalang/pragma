package cli

import pragma.domain._
import pragma.daemonProtocol._
import cats.implicits._
import scala.util._, scala.io.StdIn.readLine
import cli.utils._
import os.Path
import requests.RequestFailedException

object Main {

  def main(args: Array[String]): Unit = {
    tryOrExit(DaemonClient.ping.void)
    val config = tryOrExit(CLIConfig.parse(args.toList))
    config.command match {
      case Dev => {
        println(renderLogo)
        run(config, withReload = true)
      }
      case Prod => {
        println("Production mode is not ready yet.")
        sys.exit(1)
      }
      case CLICommand.Create => createNewProject()
      case CLICommand.Root   => ()
    }
  }

  def run(config: CLIConfig, withReload: Boolean = false): Try[Unit] = {
    lazy val code = tryOrExit(
      Try(os.read(config.filePath)),
      Some(s"Could not read ${config.filePath.toString}")
    )

    lazy val syntaxTree = SyntaxTree.from(code)

    lazy val devMigration = for {
      st <- syntaxTree
      projectName = st.config
        .entryMap("projectName")
        .value
        .asInstanceOf[PStringValue]
        .value
      functions <- st.functions.toList.traverse {
        case ExternalFunction(id, scopeName, filePathStr, runtime) => {
          val filePath = os.FilePath(filePathStr)
          for {
            (content, isBinary) <- {
              filePath match {
                case path: Path => readContent(path)
                case _ =>
                  readContent(config.projectPath / os.RelPath(filePathStr))
              }
            }
          } yield
            ImportedFunctionInput(id, scopeName, content, runtime, isBinary)
        }
        case otherFn =>
          Failure {
            new Exception {
              s"Unsupported function type `${otherFn.getClass.getCanonicalName}`"
            }
          }
      }
      migration = MigrationInput(code, functions.toList)
      _ <- DaemonClient
        .createProject(
          ProjectInput(
            name = projectName,
            secret = "DUMMY_SECRET",
            pgUri = "postgresql://localhost:5433/test",
            pgUser = "test",
            pgPassword = "test"
          )
        )
        .handleErrorWith {
          case err: RequestFailedException if err.response.statusCode == 400 =>
            Success(())
          case err => Failure(err)
        }
      _ <- DaemonClient.devMigrate(migration, projectName)
    } yield projectName

    config.command match {
      case Dev =>
        devMigration match {
          case Success(projectName) => println(welcomeMsq(projectName, Dev))
          case Failure(err)         => println(renderThrowable(err, code = Some(code)))
        }
      case _ => ()
    }

    if (withReload) reloadPrompt(config)
    else sys.exit(0)
  }

  def reloadPrompt(config: CLIConfig): Try[Unit] =
    readLine("(r)eload, (q)uit: ") match {
      case "r" | "R" => {
        println("Reloading...")
        run(config, withReload = true)
      }
      case "q" | "Q" => {
        println("Come back soon!")
        sys.exit(0)
      }
      case unknown => {
        println(s"I do not know what `$unknown` means...")
        reloadPrompt(config)
      }
    }

  def createNewProject(): Try[Unit] = Try {
    val newProjectName = readLine("What's the name of your new project?:").trim
    if (newProjectName.isEmpty) {
      println("A project's name cannot be an empty string...")
      createNewProject()
    }
    val projectDir = os.pwd / newProjectName
    val createProj =
      DaemonClient.createProject(
        ProjectInput(
          newProjectName,
          "DUMMY_SECRET",
          "postgresql://localhost:5433/test",
          "test",
          "test"
        )
      ) *> Try {
        os.makeDir(projectDir)
        val pragmafile =
          s"""
        |config { projectName = "$newProjectName" }
        |""".stripMargin
        os.write(projectDir / "Pragmafile", pragmafile)
      }

    createProj.handleErrorWith {
      case err => {
        println(renderThrowable(err))
        sys.exit(1)
      }
    }
  }

  val renderLogo =
    assets.asciiLogo
      .split("\n")
      .map(line => (" " * 24) + line)
      .mkString("\n")

}
