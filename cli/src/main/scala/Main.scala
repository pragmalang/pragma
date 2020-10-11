package cli

import pragma.domain._, pragma.domain.utils.UserError
import cats.implicits._
import org.parboiled2.{Position, ParseError}
import scala.util._

object Main {

  def main(args: Array[String]): Unit = {
    val config = tryOrExit(CLIConfig.parse(args.toList))

    config.command match {
      case CLICommand.Dev(true) => {
        println(renderLogo)
        run(config)
        os.watch
          .watch(Seq(os.Path(config.filePath.wrapped.getParent())), _ => {
            run(config)
          })
      }
      case CLICommand.Dev(false) => {
        println(renderLogo)
        tryOrExit(run(config))
      }
      case CLICommand.Prod => tryOrExit(run(config))
      case CLICommand.Root => println(CLIConfig.usageWithAsciiLogo)
    }
  }

  def run(config: CLIConfig): Try[Unit] = {
    val code = tryOrExit(
      Try(os.read(config.filePath)),
      Some(s"Could not read ${config.filePath.toString}")
    )

    SyntaxTree.from(code).as(())
  }

  def tryOrExit[A](
      t: Try[A],
      messagePrefix: Option[String] = None,
      code: Option[String] = None
  ): A =
    t match {
      case Success(value) => value
      case Failure(err) => {
        println(renderThrowable(err, messagePrefix, code))
        sys.exit(1)
      }
    }

  def renderError(
      message: String,
      position: Option[PositionRange] = None,
      code: Option[String] = None
  ): String = {
    val errSep = Console.RED + ("━" * 100) + Console.RESET
    val errTag = s"\n[${Console.RED}Error${Console.RESET}]"
    val errorCodeRegion = position match {
      case Some(
          PositionRange(
            Position(_, lineIndex, charIndex),
            Position(_, lineIndex2, charIndex2)
          )
          ) =>
        for {
          code <- code
          lines = code.split("\n").toList
          errorLine = lines(lineIndex - 1)
          msg = if (lineIndex != lineIndex2) {
            val firstErrorLine = errorLine
            val midErrorLines = lines
              .slice(lineIndex, lineIndex2 - 1)
              .map(line => line + "\n" + ("^" * line.length))
              .mkString("\n")
            val lastErrorLine = lines(lineIndex2 - 1)
            s"""|From line $lineIndex character $charIndex to line $lineIndex2 character $charIndex2
                |
                |$firstErrorLine
                |${" " * (charIndex - 1)}${"^" * ((firstErrorLine.length - 1) - charIndex)}
                |${midErrorLines}
                |${lastErrorLine}
                |${Console.RED}${"^" * charIndex2}${Console.RED}
                |""".stripMargin
          } else if (charIndex != charIndex2) {
            s"""|at line $lineIndex
                |
                |$errorLine
                |${Console.RED}${" " * (charIndex - 1)}${"^" * (charIndex2 - charIndex)}${Console.RED}
                |""".stripMargin
          } else {
            s"""|at line $lineIndex
                |
                |$errorLine
                |${Console.RED}${" " * (charIndex - 2)}^${Console.RESET}
                |""".stripMargin
          }

        } yield ": " + msg
      case _ => None
    }

    s"""|$errSep
        |$errTag $message ${errorCodeRegion.getOrElse("")}
        |$errSep""".stripMargin
  }

  def renderThrowable(
      err: Throwable,
      messagePrefix: Option[String] = None,
      code: Option[String] = None
  ): String =
    err match {
      case userErr: UserError =>
        userErr.errors
          .map {
            case (msg, pos) => renderError(msg, pos, code)
          }
          .mkString("\n")
      case ParseError(pos, pos2, _) =>
        renderError(
          "Parse error",
          Some(PositionRange(pos, pos2)),
          code
        )
      case otherErr =>
        renderError {
          messagePrefix.map(_ + "\n").getOrElse("") + otherErr.getMessage
        }
    }

  val renderLogo =
    assets.asciiLogo
      .split("\n")
      .map(line => (" " * 24) + line)
      .mkString("\n")

  val welcomeMsq = s"""
        Pragma GraphQL server is now running on port 3030

                  ${Console.GREEN}${Console.BOLD}http://localhost:3030/graphql${Console.RESET}

          Enter 'q' to quit, or anything else to reload
      """

}
