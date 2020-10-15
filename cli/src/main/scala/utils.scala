package cli

import pragma.domain.PositionRange, pragma.domain.utils.UserError
import org.parboiled2.{Position, ParseError}
import scala.util._
import java.io.{ByteArrayOutputStream, FileInputStream}
import java.util.zip.{ZipOutputStream, ZipEntry}
import java.util.Base64

object utils {

  def tryOrExit[A](
      t: Try[A],
      messagePrefix: Option[String] = None,
      code: Option[String] = None
  ): A = t match {
    case Success(value) => value
    case Failure(err) => {
      println(renderThrowable(err, messagePrefix, code))
      sys.exit(1)
    }
  }

  /** The boolean in the return means "is binary"
    * @return the content of the path. If the path leads to a directory, the content will be
    * a base64-encoded string, and true. If the path leads to a file, the file content is returned with false.
    */
  def readContent(path: os.Path): Try[(String, Boolean)] =
    if (os.isDir(path)) Try {
      val sub = os.list(path, sort = false)
      val byteOutputStream = new ByteArrayOutputStream(2000)
      val zipOutputStream = new ZipOutputStream(byteOutputStream)
      sub foreach { file =>
        zipOutputStream.putNextEntry(new ZipEntry(file.last))
        val fileInputStream = new FileInputStream(file.toString)
        zipOutputStream.write(fileInputStream.readAllBytes)
        zipOutputStream.closeEntry()
        fileInputStream.close()
      }
      val base64 = Base64.getEncoder.encode(byteOutputStream.toByteArray)
      byteOutputStream.close()
      (new String(base64), true)
    } else if (os.isLink(path)) os.followLink(path) match {
      case Some(path) => readContent(path)
      case None =>
        Failure {
          new Exception {
            s"Could not follow link ${path.toString} to a file or directory"
          }
        }
    } else Try((os.read(path), false))

  def renderError(
      message: String,
      position: Option[PositionRange] = None,
      code: Option[String] = None
  ): String = {
    val errSep = Console.RED + ("━" * 100) + Console.RESET
    val errTag = s"[${Console.RED}Error${Console.RESET}]"
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

    val indentedMessage = {
      val lines = message.split("\n")
      (lines.head +: lines.tail.map("\t" + _))
        .mkString("\n")
    }

    s"""|$errSep
        |$errTag $indentedMessage ${errorCodeRegion.getOrElse("")}
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

  def welcomeMsq(projectName: String) = s"""
    |Pragma GraphQL server is now running on port 3030
    |
    |Visit the GraphQL Playground at ${Console.GREEN}${Console.BOLD}http://localhost:3030/$projectName/graphql${Console.RESET}
    |""".stripMargin

}
