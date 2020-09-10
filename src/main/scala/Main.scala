import scala.util._
import domain._, domain.utils._
import org.parboiled2.Position
import running._
import org.parboiled2.ParseError
import cats.effect._, cats.implicits._
import doobie._, doobie.hikari._, doobie.implicits._
import running.storage.postgres._
import cli._, cli.RunMode._
import setup.schemaGenerator.ApiSchemaGenerator

object Main extends IOApp {

  // To disable logging
  org.slf4j.LoggerFactory
    .getILoggerFactory()
    .asInstanceOf[ch.qos.logback.classic.LoggerContext]
    .stop()

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
      transactor: Resource[IO, HikariTransactor[IO]],
      queryEngine: PostgresQueryEngine[IO]
  ): Resource[IO, PostgresMigrationEngine[IO]] =
    transactor map { t =>
      new PostgresMigrationEngine[IO](t, prevTree, currentTree, queryEngine)
    }

  def buildQueryEngine(
      currentTree: SyntaxTree,
      transactor: Resource[IO, HikariTransactor[IO]],
      jc: JwtCodec
  ): Resource[IO, PostgresQueryEngine[IO]] =
    transactor map { t =>
      new PostgresQueryEngine[IO](t, currentTree, jc)
    }

  def buildStorage(
      prevTree: SyntaxTree,
      currentTree: SyntaxTree,
      transactor: Resource[IO, HikariTransactor[IO]],
      jc: JwtCodec
  ): Resource[IO, Postgres[IO]] =
    for {
      qe <- buildQueryEngine(currentTree, transactor, jc)
      me <- buildMigrationEngine(prevTree, currentTree, transactor, qe)
    } yield new Postgres[IO](me, qe)

  override def run(args: List[String]): IO[ExitCode] =
    CLIConfig.parse(args) flatMap { cliConfigOption =>
      val config = cliConfigOption match {
        case Some(value) => value
        case None        => sys.exit(1)
      }

      val pwd = os.Path(config.filePath.wrapped.getParent())

      if (config.isHelp) {
        println(CLIConfig.usageWithAsciiLogo)
        sys.exit(0)
      }

      val POSTGRES_URL = sys.env.get("POSTGRES_URL")
      val POSTGRES_USER = sys.env.get("POSTGRES_USER")
      val POSTGRES_PASSWORD = sys.env.get("POSTGRES_PASSWORD")
      val PRAGMA_SECRET = sys.env.get("PRAGMA_SECRET")

      val missingEnvVars =
        List(
          ("POSTGRES_URL", POSTGRES_URL, "Your PostgreSQL DB URL"),
          ("POSTGRES_USER", POSTGRES_USER, "Your PostgreSQL DB username"),
          (
            "POSTGRES_PASSWORD",
            POSTGRES_PASSWORD,
            "Your PostgreSQL DB password"
          ),
          (
            "PRAGMA_SECRET",
            PRAGMA_SECRET,
            "A secret for your app, used for authentication"
          )
        ).collect {
          case (name, None, desc) => name -> desc
        }.toList

      val onBuildTransactorError: PartialFunction[Throwable, IO[Unit]] = _ =>
        IO {
          printError(
            "Unable to connect to PostgreSQL database, please make sure the info "
          )
          sys.exit(1)
        }

      val transactorAndJwtCodec =
        (
          config.mode,
          POSTGRES_URL,
          POSTGRES_USER,
          POSTGRES_PASSWORD,
          PRAGMA_SECRET
        ) match {
          case (_, Some(url), Some(username), Some(password), Some(secret)) =>
            IO {
              buildTransactor(url, username, password) -> new JwtCodec(secret)
            }.onError(onBuildTransactorError)
          case (RunMode.Dev, _, _, _, _) =>
            IO {
              buildTransactor(
                "localhost:5433/test",
                "test",
                "test"
              ) -> new JwtCodec("123456")
            }.onError(onBuildTransactorError)
          case (RunMode.Prod, _, _, _, _) =>
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

      val transactor = transactorAndJwtCodec.map(_._1)
      val jwtCodec = transactorAndJwtCodec.map(_._2)

      val currentCode = Try(os.read(config.filePath))

      val currentSyntaxTree = currentCode.flatMap(SyntaxTree.from)

      val currentCodeAndTree = for {
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
          val prevFilePath = pwd / ".pragma" / "prev"
          val prevTreeExists = os.exists(prevFilePath)

          val prevTree = config.mode match {
            case RunMode.Prod if (prevTreeExists) =>
              SyntaxTree.from(os.read(prevFilePath)).get
            case RunMode.Prod if (!prevTreeExists) => SyntaxTree.empty
            case RunMode.Dev                       => SyntaxTree.empty
          }

          val storage = for {
            t <- transactor
            jc <- jwtCodec
          } yield buildStorage(prevTree, currentTree, t, jc)

          val migrate: IO[Try[Unit]] =
            storage
              .flatMap { s =>
                s.use(_.migrate)
                  .map(_.toTry)
              }

          val gqlFilePath = pwd / ".pragma" / "schema.gql"

          val gqlFileExists = os.exists(gqlFilePath)

          val gqlSchema = ApiSchemaGenerator(currentTree).build.renderPretty

          val writeTsDefs = if (config.writeGqlSchema) {
            if (gqlFileExists)
              IO(os.write.over(gqlFilePath, gqlSchema))
            else if (os.exists(pwd / ".pragma"))
              IO(os.write(gqlFilePath, gqlSchema))
            else
              IO {
                os.makeDir(pwd / ".pragma")
                os.write(gqlFilePath, gqlSchema)
              }
          } else IO(())

          val writePrevTree =
            if (prevTreeExists)
              IO(os.write.over(prevFilePath, currentCode))
            else if (os.exists(pwd / ".pragma"))
              IO(os.write(prevFilePath, currentCode))
            else
              IO {
                os.makeDir(pwd / ".pragma")
                os.write(prevFilePath, currentCode)
              }

          val removeAllTables = transactor flatMap removeAllTablesFromDb recover {
            e =>
              printError(e.getMessage(), None)
              sys.exit(1)
          }

          val server = (jwtCodec, storage) mapN {
            case (jc, s) => new Server(jc, s, currentTree)
          }

          val runServer = server.flatMap(_.run(args))

          config.mode match {
            case Dev =>
              for {
                _ <- removeAllTables
                _ <- migrate
                _ <- writeTsDefs
                exitCode <- runServer
              } yield exitCode
            case Prod =>
              for {
                _ <- migrate
                _ <- writePrevTree
                _ <- writeTsDefs
                exitCode <- runServer
              } yield exitCode
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

    val styledErrorMessage =
      s"""|$errSep
          |$errTag $message ${errorCodeRegion.getOrElse("")}
          |$errSep""".stripMargin
    println(styledErrorMessage)
  }

}
