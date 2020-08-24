package cli

import scopt._
import os._
import cats.effect._
import java.io.File

case class CLIConfig(
    command: CLICommand,
    filePath: Path,
    isHelp: Boolean,
    mode: RunMode
)

object CLIConfig {
  def default =
    CLIConfig(
      command = CLICommand.RootCommand,
      filePath = os.pwd / "Pragmafile",
      isHelp = false,
      mode = RunMode.Dev
    )

  val parser: OptionParser[CLIConfig] =
    new OptionParser[CLIConfig]("pragma") {
      head {
        s"""
        |              ${Console.WHITE_B}${Console.BLACK}${Console.BOLD}Pragma${Console.RESET}
        """.stripMargin
      }

      cmd("prod")
        .action { (_, configs) =>
          configs.copy(command = CLICommand.Prod, mode = RunMode.Prod)
        }
        .text("Run app in production mode.")
        .children(fileArg)

      cmd("dev")
        .action { (_, configs) =>
          configs.copy(command = CLICommand.Dev(), mode = RunMode.Dev)
        }
        .text("Run app in development mode.")
        .children(fileArg, watchOpt)

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

      def fileArg =
        arg[File]("<file>")
          .optional()
          .action { (file, configs) =>
            configs.copy(filePath = Path(file.getAbsolutePath()))
          }
          .text(s"Defaults to ${(os.pwd / "Pragmafile").relativeTo(os.pwd)}.")

      opt[Unit]("help")
        .abbr("h")
        .optional()
        .action((_, config) => config.copy(isHelp = true))

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
}
