package cli

import scopt._
import os._
import cats.effect._
import java.io.File
import assets.asciiLogo

case class CLIConfig(
    command: CLICommand,
    filePath: Path,
    isHelp: Boolean,
    mode: RunMode,
    withTsDefs: Boolean
)

object CLIConfig {
  def default =
    CLIConfig(
      command = CLICommand.RootCommand,
      filePath = os.pwd / "Pragmafile",
      isHelp = false,
      mode = RunMode.fromEnv,
      withTsDefs = false
    )

  val parser: OptionParser[CLIConfig] =
    new OptionParser[CLIConfig]("pragma") {

      cmd("prod")
        .action { (_, configs) =>
          configs.copy(command = CLICommand.Prod, mode = RunMode.Prod)
        }
        .text("Runs the app in production mode")
        .children(fileArg, tsDefsOpt)

      cmd("dev")
        .action { (_, configs) =>
          configs.copy(command = CLICommand.Dev(), mode = RunMode.Dev)
        }
        .text("Runs the app in development mode")
        .children(fileArg, watchOpt, tsDefsOpt)

      def watchOpt =
        opt[Unit]("watch")
          .abbr("w")
          .optional()
          .action { (_, configs) =>
            configs
              .copy(command = configs.command match {
                case CLICommand.Dev(_) => CLICommand.Dev(true)
                case command           => command
              })
          }
          .text("Restarts the server on every change in <file>")

      def fileArg =
        arg[File]("<file>")
          .optional()
          .action { (file, configs) =>
            configs.copy(filePath = Path(file.getAbsolutePath()))
          }
          .text(s"Defaults to ./Pragmafile")

      def tsDefsOpt =
        opt[Unit]("ts-defs")
          .abbr("ts")
          .optional()
          .action((_, config) => config.copy(withTsDefs = true))
          .text("Generates Typescript type definitions")

      opt[Unit]("help")
        .abbr("h")
        .optional()
        .action((_, config) => config.copy(isHelp = true))
        .text("Prints usage")

      checkConfig { conf =>
        if (!conf.isHelp) {
          if (os.exists(conf.filePath) && os.isFile(conf.filePath)) success
          else if (os.isDir(conf.filePath))
            failure(s"${conf.filePath} is a directory.")
          else if (os.isLink(conf.filePath))
            failure(s"${conf.filePath} is not a file.")
          else if (conf.filePath.last == "Pragmafile" &&
                   !os.exists(conf.filePath))
            failure(s"Pragmafile doesn't exist.")
          else failure(s"${conf.filePath} doesn't exist.")
        } else success
      }
    }

  def parse(args: List[String]): IO[Option[CLIConfig]] =
    IO(parser.parse(args, CLIConfig.default))

  def usage: String = parser.renderTwoColumnsUsage

  def usageWithAsciiLogo = {
    val asciiLogoWithLeftPadding = asciiLogo
      .split("\n")
      .map(line => (" " * 24) + line)
      .mkString("\n")
    asciiLogoWithLeftPadding + "\n" + usage
  }
}

sealed trait CLICommand
object CLICommand {
  case class Dev(watch: Boolean = false) extends CLICommand
  case object Prod extends CLICommand
  case object RootCommand extends CLICommand
}

sealed trait RunMode
object RunMode {
  case object Dev extends RunMode
  case object Prod extends RunMode

  def fromEnv: RunMode = sys.env.get("PRAGMA_ENV") match {
    case Some("production")  => Prod
    case Some("prod")        => Prod
    case Some("development") => Dev
    case Some("dev")         => Dev
    case _                   => Dev
  }
}
