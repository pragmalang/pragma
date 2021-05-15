package cli

import pragma.domain._
import pragma.jwtUtils._
import cats.implicits._
import scala.util._, scala.io.StdIn.readLine
import cli.utils._
import spray.json.JsString
import pragma.parsing.PragmaParser
import daemon.server.Server
import cats.effect.IOApp
import cats.effect.{ExitCode, IO}
import pragma.envUtils._
import pragma._
import daemon.server.DBInfo
import cli.CLICommand.Dev
import cli.CLICommand.Prod
import metacall.Caller
import scala.concurrent.ExecutionContext

object Main extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {
    import EnvVars._

    val config = CLIConfig.parse(args.toList)
    config.command match {
      case Dev | Prod =>
        Caller.start(ExecutionContext.global)
        for {
          getter <- EnvVarDef.parseEnvVars(envVarsDefs, config.mode) match {
            case Left(errors) =>
              IO {
                print(EnvVarError.render(errors))
                sys.exit(1)
              }
            case Right(getter) => getter.pure[IO]
          }
          port = getter(PRAGMA_PORT).toInt
          hostname = getter(PRAGMA_HOSTNAME)
          secret = getter(PRAGMA_SECRET)
          dbInfo = DBInfo(
            host = getter(PRAGMA_PG_HOST),
            port = getter(PRAGMA_PG_PORT),
            user = getter(PRAGMA_PG_USER),
            password = getter(PRAGMA_PG_PASSWORD),
            dbName = getter(PRAGMA_PG_DB_NAME)
          )
          daemonClient = new DaemonClient(port, hostname)
          server = new Server(port, hostname, dbInfo, secret)
          exitCode <- (server.run, IO(main(config, daemonClient.some, secret.some))).parMapN {
            case (_, exitCode) =>
              exitCode match {
                case Failure(_)     => ExitCode.Error
                case Success(value) => value
              }
          }
        } yield exitCode
      case _ =>
        IO {
          main(config, None, None)
          ExitCode.Success
        }
    }

  }

  def main(
      config: CLIConfig,
      daemonClient: Option[DaemonClient],
      secret: Option[String]
  ): Try[ExitCode] = {
    config.command match {
      case CLICommand.Root | CLICommand.Help => {
        print(CLIConfig.usageWithAsciiLogo)
        Success(ExitCode.Success)
      }
      case CLICommand.Version => {
        println(cliVersion)
        Success(ExitCode.Success)
      }
      case CLICommand.Dev => {
        println(renderLogo)
        runCli(config, mode = RunMode.Dev, withReload = false, daemonClient.get)
      }
      case CLICommand.New => Try(initProject())
      case CLICommand.Prod => {
        println(renderLogo)
        runCli(config, mode = RunMode.Prod, false, daemonClient.get)
      }
      case CLICommand.GenerateRootJWT(secretOption) => {
        val jc = secretOption match {
          case Some(secret) => new JwtCodec(secret)
          case _            => new JwtCodec(secret.get)
        }
        val jwt = JwtPayload(userId = JsString("__root__"), role = "__root__")
        println(jc.encode(jwt))
        Success(ExitCode.Success)
      }
    }
  }

  def runCli(
      config: CLIConfig,
      mode: RunMode,
      withReload: Boolean = false,
      daemonClient: DaemonClient
  ): Try[ExitCode] = {
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
      _ <- daemonClient.migrate(code, config.projectPath.toString(), projectName, mode)
    } yield projectName

    migration match {
      case Success(projectName) => println(welcomeMsq(projectName, mode, daemonClient))
      case Failure(err)         => println(renderThrowable(err, code = Some(code)))
    }

    if (withReload) reloadPrompt(config, mode, daemonClient)
    else Try(haltPrompt())
  }

  def haltPrompt(): ExitCode = {
    readLine()
    ExitCode.Success
  }

  def reloadPrompt(
      config: CLIConfig,
      mode: RunMode,
      daemonClient: DaemonClient
  ): Try[ExitCode] =
    readLine("(r)eload, (q)uit: ") match {
      case "r" | "R" => {
        println("Reloading...")
        runCli(config, mode, withReload = true, daemonClient)
      }
      case "q" | "Q" => {
        println("Come back soon!")
        Success(ExitCode.Success)
      }
      case unknown => {
        println(s"I do not know what `$unknown` means...")
        reloadPrompt(config, mode, daemonClient)
      }
    }

  def initProject(): ExitCode = new PragmaParser(
    readLine("What's the name of your new project?: ").trim
  ).identifierThenEOI.run() match {
    case Failure(_) => {
      println(
        "A project's name must be a valid Pragma identifier... Please try again"
      )
      ExitCode.Error
    }
    case Success(newProjectName) => {

      val projectDir = os.pwd / newProjectName
      os.makeDir(projectDir)
      val pragmafile =
        s"""
            |config { projectName = "$newProjectName" }
            |""".stripMargin

      os.write(projectDir / "Pragmafile", pragmafile)
      os.write(projectDir / ".pragma" / "version.json", cliVersionJsonStr)
      println("Project files successfully generated.")
      ExitCode.Success
    }
  }

  lazy val renderLogo =
    assets.asciiLogo
      .split("\n")
      .map(line => (" " * 24) + line)
      .mkString("\n")

}
