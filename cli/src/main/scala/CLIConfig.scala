package cli

import scopt._
import os._
import java.io.File
import assets.asciiLogo
import pragma.RunMode

case class CLIConfig(
    command: CLICommand,
    filePath: Path
) {
  val projectPath = os.Path(filePath.wrapped.getParent().toAbsolutePath())
  val dotPragmaDir = projectPath / ".pragma"
  val mode = command match {
    case CLICommand.Prod => RunMode.Prod
    case _               => RunMode.Dev
  }
}

object CLIConfig {
  val default =
    CLIConfig(
      command = CLICommand.Root,
      filePath = os.pwd / "Pragmafile"
    )

  val parser: OptionParser[CLIConfig] =
    new OptionParser[CLIConfig]("pragma") {
      cmd("new").children(newProject)

      def newProject =
        cmd("project")
          .action { (_, configs) =>
            configs.copy(command = CLICommand.New)
          }
          .text("Create a new project")

      cmd("dev")
        .action { (_, configs) =>
          configs.copy(command = CLICommand.Dev)
        }
        .text("Runs the app in development mode")
        .children(fileArg)

      cmd("prod")
        .action { (_, configs) =>
          configs.copy(command = CLICommand.Prod)
        }
        .text("Runs the app in production mode (Not Implemented)")
        .children(fileArg)

      cmd("help")
        .action { (_, configs) =>
          configs.copy(command = CLICommand.Help)
        }
        .text("Prints usage text")

      def fileArg =
        arg[File]("<file>")
          .optional()
          .action { (file, configs) =>
            configs.copy(filePath = Path(file.getAbsolutePath()))
          }
          .text(s"Defaults to ./Pragmafile")

      def secretArg =
        arg[Option[String]]("<secret>")
          .optional()
          .action { (secret, configs) =>
            configs.copy(command = CLICommand.GenerateRootJWT(secret))
          }
          .text(s"The application secret")

      cmd("root-jwt")
        .text("Generates an authorization JWT with root privileges")
        .children(secretArg)

      cmd("version")
        .text("Prints the Pragma CLI version")
        .action { (_, config) =>
          config.copy(command = CLICommand.Version)
        }

      opt[Unit]("help")
        .abbr("h")
        .optional()
        .action((_, config) => config.copy(command = CLICommand.Help))
        .text("Prints usage text")
    }

  def parse(args: List[String]): CLIConfig =
    parser.parse(args, CLIConfig.default).getOrElse(sys.exit(1))

  def usage: String = parser.usage + "\n"

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
  case object New extends CLICommand
  case object Root extends CLICommand
  case object Help extends CLICommand
  case object Version extends CLICommand
  case class GenerateRootJWT(secret: Option[String]) extends CLICommand
  case object Dev extends CLICommand
  case object Prod extends CLICommand
}
