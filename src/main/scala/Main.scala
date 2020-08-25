import scala.util._
import domain.utils._
import org.parboiled2.Position
import domain._
import running.Server
import org.parboiled2.ParseError
import cats.effect._
import cats.implicits._
import doobie._, doobie.hikari._, doobie.implicits._
import running.storage.postgres._
import cli._
import cli.RunMode.Dev
import cli.RunMode.Prod

object Main extends IOApp {

  // To disable logging
  import org.slf4j.LoggerFactory
  import ch.qos.logback.classic.LoggerContext
  val loggerCtx = LoggerFactory.getILoggerFactory().asInstanceOf[LoggerContext]
  loggerCtx.stop()

  def removeAllTablesFromDb(
      transactor: Resource[IO, HikariTransactor[IO]]
  )(implicit bracket: Bracket[IO, Throwable]): IO[Unit] =
    transactor.use { t =>
      Fragment(
        s"""|DROP SCHEMA public CASCADE;
            |CREATE SCHEMA public;
            |GRANT ALL ON SCHEMA public TO ${t.kernel.getUsername()};
            |GRANT ALL ON SCHEMA public TO public;
            |""".stripMargin,
        Nil
      ).update.run.transact(t)
    }.void

  def buildTransactor(
      uri: String,
      username: String,
      password: String
  ): Resource[IO, HikariTransactor[IO]] = {
    for {
      ce <- ExecutionContexts.fixedThreadPool[IO](32)
      be <- Blocker[IO]
      t <- HikariTransactor.newHikariTransactor[IO](
        "org.postgresql.Driver",
        if (uri.startsWith("postgresql://"))
          s"jdbc:$uri"
        else
          s"jdbc:postgresql://$uri",
        username,
        password,
        ce,
        be
      )
    } yield t
  }

  def buildMigrationEngine(
      prevTree: SyntaxTree,
      currentTree: SyntaxTree,
      transactor: Resource[IO, HikariTransactor[IO]]
  ): Resource[IO, PostgresMigrationEngine[IO]] =
    transactor map { t =>
      new PostgresMigrationEngine[IO](t, prevTree, currentTree)
    }

  def buildQueryEngine(
      currentTree: SyntaxTree,
      transactor: Resource[IO, HikariTransactor[IO]]
  ): Resource[IO, PostgresQueryEngine[IO]] =
    transactor map { t =>
      new PostgresQueryEngine[IO](t, currentTree)
    }

  def buildStorage(
      prevTree: SyntaxTree,
      currentTree: SyntaxTree,
      transactor: Resource[IO, HikariTransactor[IO]]
  ): Resource[IO, Postgres[IO]] =
    for {
      qe <- buildQueryEngine(currentTree, transactor)
      me <- buildMigrationEngine(prevTree, currentTree, transactor)
    } yield new Postgres[IO](me, qe)

  override def run(args: List[String]): IO[ExitCode] =
    CLIConfig.parse(args) flatMap { cliConfigOption =>
      val config = cliConfigOption match {
        case Some(value) => value
        case None        => sys.exit(1)
      }

      if (config.isHelp) {
        println(CLIConfig.usage)
        sys.exit(0)
      }

      val POSTGRES_URL = sys.env.get("POSTGRES_URL")
      val POSTGRES_USER = sys.env.get("POSTGRES_USER")
      val POSTGRES_PASSWORD = sys.env.get("POSTGRES_PASSWORD")

      val missingEnvVars =
        List(
          ("POSTGRES_URL", POSTGRES_URL, "Your PostgreSQL DB URL"),
          ("POSTGRES_USER", POSTGRES_USER, "Your PostgreSQL DB username"),
          (
            "POSTGRES_PASSWORD",
            POSTGRES_PASSWORD,
            "Your PostgreSQL DB password"
          )
        ).filter(!_._2.isDefined)
          .map(v => v._1 -> v._3)
          .toList

      val onBuildTransactorError: PartialFunction[Throwable, IO[Unit]] = _ =>
        IO {
          printError(
            "Unable to connect to PostgreSQL database, please make sure the info "
          )
          sys.exit(1)
        }

      val transactor =
        (config.mode, POSTGRES_URL, POSTGRES_USER, POSTGRES_PASSWORD) match {
          case (_, Some(url), Some(username), Some(password)) =>
            IO(buildTransactor(url, username, password))
              .onError(onBuildTransactorError)
          case (RunMode.Dev, _, _, _) =>
            IO(buildTransactor("localhost:5433/test", "test", "test"))
              .onError(onBuildTransactorError)
          case (RunMode.Prod, _, _, _) =>
            IO {
              val isPlural = missingEnvVars.length > 1
              val `variable/s` = if (isPlural) "variables" else "variable"
              val `is/are` = if (isPlural) "are" else "is"
              val missingVarNames = missingEnvVars map (_._1)
              val renderedVarsWithDescription =
                missingEnvVars.map(v => s"${v._1}=<${v._2}>").mkString(" ")
              val renderedVarNames = missingVarNames.mkString(", ")
              val renderedCliArgs = args.mkString(" ")
              val errMsg =
                s"""
              |Environment ${`variable/s`} $renderedVarNames ${`is/are`} must be specified when in production mode.
              |Try: $renderedVarsWithDescription pragma $renderedCliArgs
              """.stripMargin

              printError(errMsg, None)
              sys.exit(1)
            }
        }

      val currentCode = Try(os.read(config.filePath))

      val currentSyntaxTree = currentCode.flatMap(SyntaxTree.from)

      val currentCodeAndTree =
        for {
          code <- currentCode
          currentSyntaxTree <- currentSyntaxTree
        } yield (currentSyntaxTree, code)

      currentCodeAndTree match {
        case Failure(userErr: UserError) =>
          IO {
            userErr.errors foreach { err =>
              printError(err._1, err._2, currentCode.toOption)
            }
            ExitCode.Error
          }
        case Failure(ParseError(pos, pos2, _)) =>
          IO {
            printError(
              "Parse error",
              Some(PositionRange(pos, pos2)),
              currentCode.toOption
            )
            ExitCode.Error
          }
        case Failure(otherErr) =>
          IO {
            printError(otherErr.getMessage, None)
            ExitCode.Error
          }

        case Success((currentTree, currentCode)) => {
          val prevFilePath = os.pwd / ".pragma" / "prev"
          val prevTreeExists = os.exists(prevFilePath)

          val prevTree = config.mode match {
            case RunMode.Prod if (prevTreeExists) =>
              SyntaxTree.from(os.read(prevFilePath)).get
            case RunMode.Prod if (!prevTreeExists) => SyntaxTree.empty
            case RunMode.Dev                       => SyntaxTree.empty
          }

          val storage =
            transactor.map(t => buildStorage(prevTree, currentTree, t))

          val migrate = storage
            .flatMap { s =>
              s.use(_.migrate)
                .map(_.toTry)
            }

          val writePrevTree =
            if (prevTreeExists)
              IO(os.write.over(prevFilePath, currentCode))
            else if (os.exists(os.pwd / ".pragma"))
              IO(os.write(prevFilePath, currentCode))
            else
              IO {
                os.makeDir(os.pwd / ".pragma")
                os.write(prevFilePath, currentCode)
              }

          val removeAllTables = transactor flatMap removeAllTablesFromDb recover {
            e =>
              printError(e.getMessage(), None)
              sys.exit(1)
          }

          val server = storage.map(new Server(_, currentTree))

          config.mode match {
            case Dev =>
              removeAllTables *> migrate *> server.flatMap(_.run(args))
            case Prod =>
              migrate *> writePrevTree *> server.flatMap(_.run(args))
          }
        }
      }
    }

  lazy val errSep = Console.RED + ("━" * 100) + Console.RESET

  def printError(
      message: String,
      position: Option[PositionRange] = None,
      code: Option[String] = None
  ) = {
    val errTag = s"\n[${Console.RED}error${Console.RESET}]"
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
                |${"^" * charIndex2}
                |""".stripMargin
          } else if (charIndex != charIndex2) {
            s"""|at line $lineIndex
                |
                |$errorLine
                |${" " * (charIndex - 1)}${"^" * (charIndex2 - charIndex)}
                |""".stripMargin
          } else {
            s"""|at line $lineIndex
                |
                |$errorLine
                |${" " * (charIndex - 2)}^
                |""".stripMargin
          }

        } yield ": " + msg
      case _ => None
    }

    val styledErrorMessage =
      s"""|$errSep
          |$errTag $message ${errorCodeRegion.getOrElse("")}
          |$errSep""".stripMargin
    println(styledErrorMessage)
  }

}
