import scala.util._
import domain.utils._
import org.parboiled2.Position
import domain._
import running.Server
import org.parboiled2.ParseError
import cats.effect._
import doobie._, doobie.hikari._
import running.storage.postgres._
import cli.CLIConfigs
import cats.implicits._

object Main extends IOApp {

  def buildTransactor(
      uri: String,
      username: String,
      password: String
  ): Resource[IO, HikariTransactor[IO]] =
    for {
      ce <- ExecutionContexts.fixedThreadPool[IO](32)
      be <- Blocker[IO]
      t <- HikariTransactor.newHikariTransactor[IO](
        "org.postgresql.Driver",
        if (!uri.startsWith("postgresql://"))
          s"jdbc:postgresql://$uri"
        else
          s"jdbc:$uri",
        username,
        password,
        ce,
        be
      )
    } yield t

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

  override def run(args: List[String]): IO[ExitCode] = {
    val POSTGRES_URL = sys.env.get("POSTGRES_URL")
    val POSTGRES_USER = sys.env.get("POSTGRES_USER")
    val POSTGRES_PASSWORD = sys.env.get("POSTGRES_PASSWORD")

    val missingEnvVars =
      List(
        ("POSTGRES_URL", POSTGRES_URL, "Your PostgreSQL DB URL"),
        ("POSTGRES_USER", POSTGRES_USER, "Your PostgreSQL DB username"),
        ("POSTGRES_PASSWORD", POSTGRES_PASSWORD, "Your PostgreSQL DB password")
      ).filter(_._2.isDefined)
        .map(v => v._1 -> v._3)
        .toList

    val transactor = (POSTGRES_URL, POSTGRES_USER, POSTGRES_PASSWORD) match {
      case (Some(url), Some(username), Some(password)) =>
        IO(buildTransactor(url, username, password))
      case _ =>
        IO {
          val isPlural = missingEnvVars.length > 1
          val `variable or variables` =
            if (isPlural) "variables" else "variable"
          val `is or are` = if (isPlural) "are" else "is"
          val missingEnvVarNames = missingEnvVars map (_._1)
          val renderedEnvVars =
            missingEnvVars.map(v => s"${v._1}=<${v._2}>").mkString(" ")
          val errMsg =
            s"""
       |Environment ${`variable or variables`} ${missingEnvVarNames.mkString(
                 ", "
               )} ${`is or are`} not specified.
       |Try: $renderedEnvVars pragma ${args.mkString(" ")}
       """.stripMargin
          printError(errMsg, None)
          sys.exit(1)
        }
    }

    val cliConfigs = CLIConfigs.parse(args)

    val currentCodeAndTree = for {
      configOption <- cliConfigs
      code = configOption
        .traverse { config =>
          Try(os.read(config.filePath))
        }
        .collect { case Some(value) => value }
      currentSyntaxTree = code.flatMap { code =>
        SyntaxTree.from(code)
      }
    } yield
      for {
        code <- code
        currentSyntaxTree <- currentSyntaxTree
      } yield (currentSyntaxTree, code)

    currentCodeAndTree.flatMap {
      case Failure(userErr: UserError) =>
        IO {
          userErr.errors foreach (err => printError(err._1, err._2))
          ExitCode.Error
        }
      case Failure(ParseError(pos, _, _)) =>
        IO {
          printError("Parse error", Some(PositionRange(pos, pos)))
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

        val prevTree =
          if (prevTreeExists)
            SyntaxTree.from(os.read(prevFilePath)).get
          else
            SyntaxTree.empty

        val storage =
          transactor.map(t => buildStorage(prevTree, currentTree, t))

        val migrationResult = storage
          .flatMap(
            _.use(_.migrate)
              .map(_.toTry)
          )

        val writePrevTree: IO[Unit] = migrationResult flatMap {
          case Failure(exception) =>
            IO { printError(exception.getMessage(), None) }
          case Success(_) if prevTreeExists =>
            IO { os.write.over(prevFilePath, currentCode) }
          case Success(_) if !prevTreeExists =>
            IO {
              os.makeDir(os.pwd / ".pragma")
              os.write(prevFilePath, currentCode)
            }
        }

        val server = storage.map(new Server(_, currentTree))

        writePrevTree *> server.flatMap(_.run(args))
      }
    }
  }

  lazy val errSep = Console.RED + ("━" * 100) + Console.RESET

  def printError(message: String, position: Option[PositionRange]) = {
    println(errSep)
    print("[error] ")
    println(message)
    position match {
      case Some(
          PositionRange(
            Position(_, line, char),
            Position(_, line2, char2)
          )
          ) => {
        print('\t')
        println(
          s"(at line $line character $char until line $line2 character $char2)"
        )
      }
      case _ => ()
    }
    println(errSep)
  }
}
