package cli

import pragma.domain._, pragma.domain.utils._
import pragma.daemonProtocol._, pragma.jwtUtils._
import cats.implicits._
import scala.util._, scala.io.StdIn.readLine
import cli.utils._
import os.Path
import requests.RequestFailedException
import spray.json.JsString
import pragma.parsing.PragmaParser

object Main {

  def main(args: Array[String]): Unit = {
    val config = CLIConfig.parse(args.toList)

    config.command match {
      case CLICommand.Root | CLICommand.Help => {
        print(CLIConfig.usageWithAsciiLogo)
        sys.exit(0)
      }
      case CLICommand.Version => {
        println(cliVersion)
        sys.exit(0)
      }
      case Dev => {
        tryOrExit(
          updateDockerCompose(config) *> pingOrStartDevDaemon(config),
          Some(
            "Failed to reach or start a local Pragma instance for development"
          )
        )
        println(renderLogo)
        run(config, mode = Dev, withReload = true)
      }
      case CLICommand.New => initProject()
      case Prod => {
        tryOrExit(DaemonClient.pingLocalDaemon().void)
        println(renderLogo)
        run(config, mode = Prod)
      }
      case CLICommand.GenerateRootJWT(secret) => {
        val jc = new JwtCodec(secret)
        val jwt = JwtPayload(userId = JsString("__root__"), role = "__root__")
        println(jc.encode(jwt))
        sys.exit(0)
      }
    }
  }

  def run(
      config: CLIConfig,
      mode: RunMode,
      withReload: Boolean = false
  ): Try[Unit] = {
    lazy val code = tryOrExit(
      Try(os.read(config.filePath)),
      Some(s"Could not read ${config.filePath.toString}")
    )

    lazy val syntaxTree = SyntaxTree.from(code)

    val migration = for {
      st <- syntaxTree
      projNameEntry = st.config.entryMap("projectName")
      projectName = projNameEntry.value
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
          } yield ImportedFunctionInput(id, scopeName, content, runtime, isBinary)
        }
        case otherFn =>
          Failure {
            new Exception {
              s"Unsupported function type `${otherFn.getClass.getCanonicalName}`"
            }
          }
      }
      _ <- DaemonClient
        .createProject(ProjectInput(projectName), mode)
        .handleErrorWith {
          // Meaning project already exists
          case err: RequestFailedException if err.response.statusCode == 400 =>
            Success(())
          case err =>
            Failure(
              new Exception(s"Unable to create project\n${err.getMessage}")
            )
        }
        .void
      usedRuntimes <- usedFuntionRuntimes(st.imports.toList).toTry
      _ <- pullDockerRuntimeImages(usedRuntimes)
      migration = MigrationInput(code, functions.toList, "DUMMY_SECRET")
      _ <- DaemonClient.migrate(migration, projectName, mode)
    } yield projectName

    migration match {
      case Success(projectName) => println(welcomeMsq(projectName, mode))
      case Failure(err)         => println(renderThrowable(err, code = Some(code)))
    }

    if (withReload) reloadPrompt(config, mode)
    else sys.exit(0)
  }

  def pullDockerRuntimeImages(runtimes: Set[RuntimeTag]): Try[Unit] = Try {
    import RuntimeTag._
    runtimes.toList.traverse { runtime =>
      val imageName = runtime match {
        case NodeJS10 => "openwhisk/action-nodejs-v10:nightly"
        case NodeJS14 => "openwhisk/action-nodejs-v14:nightly"
        case Python3  => "openwhisk/python3action:nightly"
      }

      Try {
        println(s"Pulling image $imageName...")
        os.proc("docker", "pull", imageName).call(cwd = os.pwd)
      } handleErrorWith { err =>
        Failure {
          new Exception(s"Failed to pull image $imageName\n${err.getMessage}")
        }
      }
    }
  }

  def reloadPrompt(config: CLIConfig, mode: RunMode): Try[Unit] =
    readLine("(r)eload, (q)uit: ") match {
      case "r" | "R" => {
        println("Reloading...")
        run(config, mode, withReload = true)
      }
      case "q" | "Q" => {
        println("Come back soon!")
        sys.exit(0)
      }
      case unknown => {
        println(s"I do not know what `$unknown` means...")
        reloadPrompt(config, mode)
      }
    }

  def initProject(): Try[Unit] = Try {
    val newProjectName = new PragmaParser(
      readLine("What's the name of your new project?: ").trim
    ).identifierThenEOI.run() match {
      case Failure(_) => {
        println(
          "A project's name must be a valid Pragma identifier... Please try again"
        )
        sys.exit(1)
      }
      case Success(id) => id
    }
    val projectDir = os.pwd / newProjectName
    Try {
      os.makeDir(projectDir)
      val pragmafile =
        s"""
        |config { projectName = "$newProjectName" }
        |""".stripMargin

      os.write(projectDir / "Pragmafile", pragmafile)
      os.write(
        projectDir / ".pragma" / "docker-compose.yml",
        dockerComposeFile,
        createFolders = true
      )
      os.write(projectDir / ".pragma" / "version.json", cliVersionJsonStr)
    } *> Success(println("Project files successfully generated."))
  }

  def pingOrStartDevDaemon(config: CLIConfig): Try[Unit] =
    DaemonClient.pingLocalDaemon().void.recoverWith { _ =>
      val dcyml = config.dotPragmaDir / "docker-compose.yml"
      def dcUp =
        Try {
          println("Starting required Docker containers...")
          os.proc("docker-compose", "up", "-d").call(config.dotPragmaDir)
        }.void
          .adaptErr { err =>
            new Exception(
              s"`docker-compose up` failed on ${dcyml.toString}\n${err.getMessage}"
            )
          }

      if (os.exists(dcyml)) dcUp *> DaemonClient.pingLocalDaemon(10).void
      else
        Try {
          println(s"Creating ${dcyml.toString}...")
          os.write(dcyml, dockerComposeFile, createFolders = true)
        } *> dcUp
    }

  lazy val renderLogo =
    assets.asciiLogo
      .split("\n")
      .map(line => (" " * 24) + line)
      .mkString("\n")

}
