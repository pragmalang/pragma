package cli

import pragma.domain._, pragma.domain.utils._
import org.parboiled2.{Position, ParseError}
import spray.json._
import java.io.ByteArrayOutputStream
import java.util.zip.{ZipOutputStream, ZipEntry}
import scala.util._
import cats.implicits._
import java.util.Base64
import pragma.RunMode

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

  /** Recursively measures the size of a file/directory */
  private def size(path: os.Path): Long = {
    if (os.isDir(path))
      os.walk(path)
        .filterNot(path => os.isDir(path) || os.isLink(path))
        .foldLeft(0L)((acc, filePath) => acc + os.size(filePath))
    else if (os.isLink(path)) {
      os.followLink(path) match {
        case Some(value) => size(value)
        case None        => 0
      }
    } else os.size(path)
  }

  /** Returns a base-64 encoded string containing the zipped directory */
  private def zipDir(path: os.Path) =
    Try {
      if (!os.isDir(path))
        throw new Exception(
          s"Trying to zip non-directory '$path' as a directory (which it's not)"
        )
      val bos = new ByteArrayOutputStream(size(path).toInt)
      val zos = new ZipOutputStream(bos)
      val paths = os.walk(path)
      paths.map(_.relativeTo(path)).foreach { child =>
        if (os.isDir(child.resolveFrom(path)))
          zos.putNextEntry(new ZipEntry(child.toString + "/"))
        if (os.isFile(child.resolveFrom(path))) {
          zos.putNextEntry(new ZipEntry(child.toString))
          val childContent = os.read(child.resolveFrom(path)).getBytes
          zos.write(childContent, 0, childContent.length)
        }
        zos.closeEntry()
      }
      zos.close()
      new String(Base64.getEncoder.encode(bos.toByteArray))
    }.adaptErr { case e: Exception =>
      new Exception(s"Error while zipping $path\n${e.getMessage}")
    }

  /** The boolean in the return means "is binary"
    * @return the content of the path. If the path leads to a directory, the content will be
    * a base64-encoded string, and true. If the path leads to a file, the file content is returned with false.
    */
  def readContent(path: os.Path): Try[(String, Boolean)] =
    if (os.isDir(path)) Try {
      (zipDir(path).get, true)
    }
    else if (os.isLink(path)) os.followLink(path) match {
      case Some(path) => readContent(path)
      case None =>
        Failure {
          new Exception {
            s"Could not follow link ${path.toString} to a file or directory"
          }
        }
    }
    else Try((os.read(path), false))

  def dockerComposeTemplate: String = scala.io.Source
    .fromResource("docker-compose-template.yml")
    .getLines()
    .mkString("\n")

  def dockerComposeFile: String =
    dockerComposeTemplate.replace("$pragma_version", cliVersion)

  def cliVersionJsonStr =
    scala.io.Source.fromResource("version.json").getLines().mkString("\n")

  lazy val cliVersion: String = {
    cliVersionJsonStr.parseJson match {
      case JsString(version) => version
      case _ => {
        println("Could not parse Pragma CLI version. Please report this issue")
        sys exit 1
      }
    }
  }

  def updateDockerCompose(config: CLIConfig): Try[Unit] =
    Try(os.read(config.dotPragmaDir / "docker-compose.yml")).flatMap {
      currentComposeFile =>
        val updatedComposeFile = dockerComposeFile
        if (currentComposeFile == updatedComposeFile) Success(())
        else
          Try(os.read(config.dotPragmaDir / "version.json"))
            .recover { _ =>
              os.write(
                config.dotPragmaDir / "version.json",
                cliVersionJsonStr
              )
              cliVersionJsonStr
            }
            .map { currentVersionJson =>
              if (Try(currentVersionJson.parseJson) == Success(JsString(cliVersion)))
                println(
                  "docker-compose file has been modified. Automatic updates will not be performed on .pragma/docker-compose.yml."
                )
              else {
                os.write(
                  config.dotPragmaDir / "docker-compose.yml",
                  dockerComposeFile,
                  createFolders = true
                )
                os.write(config.dotPragmaDir / "version.json", cliVersionJsonStr)
                println("Pulling Docker image updates...")
                os.proc("docker-compose", "pull").call(config.dotPragmaDir)
              }
            }
    }

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
          msg =
            if (lineIndex != lineIndex2) {
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
          .map { case (msg, pos) =>
            renderError(msg, pos, code)
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

  def welcomeMsq(projectName: String, mode: RunMode, daemonClient: DaemonClient) = {
    val apiUrlMsg = mode match {
      case RunMode.Dev =>
        s"Visit the GraphQL Playground at ${Console.GREEN}${Console.BOLD}${daemonClient.daemonUri}/project/$projectName/dev/graphql${Console.RESET}"
      case RunMode.Prod =>
        s"Your API is now available at ${daemonClient.daemonUri}/project/$projectName/prod/graphql"
    }

    s"""
       |Pragma GraphQL server successfully started!
       |$apiUrlMsg
       |""".stripMargin
  }

  def usedFuntionRuntimes(
      imports: List[PImport]
  ): Either[UserError, Set[RuntimeTag]] = {
    def parseRuntime(
        s: String,
        position: Option[PositionRange]
    ): Either[ErrorMessage, RuntimeTag] =
      supportedFunctionRuntimes
        .get(s)
        .fold[Either[ErrorMessage, RuntimeTag]] {
          val supportedRuntimesStr =
            supportedFunctionRuntimes.keys
              .map(s => s""""$s"""")
              .mkString(", ")
          (
            s"Invalid `runtime` value `$s` for key `runtime`\nValid `runtime` values are: $supportedRuntimesStr",
            position
          ).asLeft
        }(_.asRight)

    def impRuntime(imp: PImport) =
      imp.config.fold[Either[ErrorMessage, RuntimeTag]] {
        (s"Missing configuration of import `${imp.id}`", imp.position).asLeft
      } { config =>
        config.entryMap("runtime") match {
          case ConfigEntry(_, PStringValue(value), pos) =>
            parseRuntime(value, pos)
          case other =>
            (
              s"Invalid non `String` value for config key `runtime` in import `${imp.id}`",
              other.position
            ).asLeft
        }
      }

    val (errors, runtimes) = imports.map(impRuntime).partitionMap(identity)

    if (errors.isEmpty) runtimes.toSet.asRight
    else UserError(errors).asLeft
  }

}
