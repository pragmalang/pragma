package cli

import scopt._
import os._
import java.io.File
import assets.asciiLogo
import scala.util.Try

case class CLIConfig(
    command: CLICommand,
    filePath: Path,
    isHelp: Boolean
) {
  val projectPath = os.Path(filePath.wrapped.getParent().toAbsolutePath())
}

object CLIConfig {
  val default =
    CLIConfig(
      command = CLICommand.Root,
      filePath = os.pwd / "Pragmafile",
      isHelp = false
    )

  val parser: OptionParser[CLIConfig] =
    new OptionParser[CLIConfig]("pragma") {
      cmd("prod")
        .action { (_, configs) =>
          configs.copy(command = CLICommand.Prod)
        }
        .text("Runs the app in production mode")
        .children(fileArg)

      cmd("dev")
        .action { (_, configs) =>
          configs.copy(command = CLICommand.Dev)
        }
        .text("Runs the app in development mode")
        .children(fileArg)

      cmd("create")
        .action { (_, configs) =>
          configs.copy(command = CLICommand.Create)
        }
        .text("Initialize a new project")

      def fileArg =
        arg[File]("<file>")
          .optional()
          .action { (file, configs) =>
            configs.copy(filePath = Path(file.getAbsolutePath()))
          }
          .text(s"Defaults to ./Pragmafile")

      opt[Unit]("help")
        .abbr("h")
        .optional()
        .action((_, config) => config.copy(isHelp = true))
        .text("Prints usage")
    }

  def parse(args: List[String]): Try[CLIConfig] =
    Try(parser.parse(args, CLIConfig.default).getOrElse(CLIConfig.default))

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
  case object Dev extends CLICommand
  case object Prod extends CLICommand
  case object Create extends CLICommand
  case object Root extends CLICommand
}
