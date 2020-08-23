package cli

import scopt._
import os._
import cats.effect._
import java.io.File

case class CLIConfig(
    command: CLICommand = CLICommand.Dev(false),
    filePath: Path = os.pwd / "Pragmafile"
)

object CLIConfig {
  def default = CLIConfig()

  val parser: OptionParser[CLIConfig] =
    new OptionParser[CLIConfig]("pragma") {
      head {
        s"""
        |              ${Console.WHITE_B}${Console.BLACK}${Console.BOLD}Pragma${Console.RESET}
        """.stripMargin
      }

      cmd("prod")
        .action((_, configs) => configs.copy(command = CLICommand.Prod))
        .text("Run app in production mode.")
        .children(fileArg)

      cmd("dev")
        .action((_, configs) => configs.copy(command = CLICommand.Dev()))
        .text("Run app in development mode.")
        .children(fileArg, watchOpt)

      def watchOpt =
        opt[Unit]("watch")
          .optional()
          .action { (_, configs) =>
            configs
              .copy(command = configs.command match {
                case CLICommand.Dev(_) => CLICommand.Dev(true)
                case CLICommand.Prod   => CLICommand.Prod
              })
          }

      def fileArg =
        arg[File]("<file>")
          .optional()
          .action { (file, configs) =>
            configs.copy(filePath = Path(file.getAbsolutePath()))
          }
          .text(s"Defaults to ${os.pwd / "Pragmafile"}.")

      checkConfig { conf =>
        if (os.exists(conf.filePath) && os.isFile(conf.filePath)) success
        else if (os.isDir(conf.filePath))
          failure(s"${conf.filePath} is a directory.")
        else if (os.isLink(conf.filePath))
          failure(s"${conf.filePath} is not a file.")
        else if (conf.filePath.last == "Pragmafile" &&
                 !os.exists(conf.filePath))
          failure(s"Pragmafile doesn't exist.")
        else failure(s"${conf.filePath} doesn't exist.")
      }
    }

  def parse(args: List[String]): IO[Option[CLIConfig]] =
    IO(parser.parse(args, CLIConfig.default))

  def usage: String = parser.renderOneColumnUsage
}

sealed trait CLICommand {
  def run(config: CLIConfig): IO[ExitCode]
}
object CLICommand {
  case class Dev(watch: Boolean = false) extends CLICommand {
    override def run(config: CLIConfig): IO[ExitCode] = ???
  }

  case object Prod extends CLICommand {
    override def run(config: CLIConfig): IO[ExitCode] = ???
  }
}
