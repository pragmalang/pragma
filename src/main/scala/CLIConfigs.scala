package cli

import scopt._
import os._
import cats.effect._
import java.io.File

case class CLIConfigs(
    mode: RunMode,
    filePath: Path
)

object CLIConfigs {
  def empty = CLIConfigs(RunMode.Dev(false), os.pwd / "Pragmafile")

  val parser: OptionParser[CLIConfigs] =
    new OptionParser[CLIConfigs]("pragma") {
      head {
        s"""
        |              ${Console.WHITE_B}${Console.BLACK}${Console.BOLD}Pragma${Console.RESET}
        """.stripMargin
      }

      cmd("prod")
        .action((_, configs) => configs.copy(mode = RunMode.Prod))
        .text("Run app in production mode.")
        .children(fileArg)

      cmd("dev")
        .action((_, configs) => configs.copy(mode = RunMode.Dev()))
        .text("Run app in development mode.")
        .children(fileArg, watchOpt)

      def watchOpt =
        opt[Unit]("watch")
          .optional()
          .action { (_, configs) =>
            configs
              .copy(mode = configs.mode match {
                case RunMode.Dev(_) => RunMode.Dev(true)
                case RunMode.Prod   => RunMode.Prod
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

  def parse(args: List[String]): IO[Option[CLIConfigs]] =
    IO(parser.parse(args, CLIConfigs.empty))

  def usage: String = parser.renderOneColumnUsage
}

sealed trait RunMode
object RunMode {
  case class Dev(watch: Boolean = false) extends RunMode
  case object Prod extends RunMode
}
