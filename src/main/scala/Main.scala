import scala.util._
import domain.utils._
import org.parboiled2.Position
import domain._
import running.Server
import org.parboiled2.ParseError
import cats.effect._
import os.Path
import doobie._, doobie.hikari._
import running.storage.postgres._

object Main extends IOApp {
  val transactor: Resource[IO, HikariTransactor[IO]] =
    for {
      ce <- ExecutionContexts.fixedThreadPool[IO](32)
      be <- Blocker[IO]
      t <- HikariTransactor.newHikariTransactor[IO](
        "org.postgresql.Driver",
        "jdbc:postgresql://localhost:5433/test",
        "test",
        "test",
        ce,
        be
      )
    } yield t

  def migrationEngine(
      prevTree: SyntaxTree,
      currentTree: SyntaxTree
  ): Resource[IO, PostgresMigrationEngine[IO]] =
    transactor map { t =>
      new PostgresMigrationEngine[IO](t, prevTree, currentTree)
    }

  def queryEngine(
      currentTree: SyntaxTree
  ): Resource[IO, PostgresQueryEngine[IO]] =
    transactor map { t =>
      new PostgresQueryEngine[IO](t, currentTree)
    }

  def storage(
      prevTree: SyntaxTree,
      currentTree: SyntaxTree
  ): Resource[IO, Postgres[IO]] =
    for {
      qe <- queryEngine(currentTree)
      me <- migrationEngine(prevTree, currentTree)
    } yield new Postgres[IO](me, qe)

  override def run(args: List[String]): IO[ExitCode] = {
    val defaultFilePath = os.pwd / "Pragmafile"
    val filePath: IO[Try[Path]] = IO {
      if (!args.isEmpty && os.exists(os.Path(args(0))))
        Success(os.Path(args(0)))
      else if (os.exists(defaultFilePath))
        Success(defaultFilePath)
      else if (!args.isEmpty && !os.exists(os.Path(args(0))))
        Failure(
          UserError(s"File ${(os.Path(args(0)))} doesn't exist")
        )
      else
        Failure(
          UserError(s"""
            |File `Pragmafile` was not found in working directory, please create one, or pass the path
            |of another file as an argument. Ex:
            |
            |
            |       pragma run ${os.pwd / "myfile.pragma"}
            |
            |
            """.stripMargin)
        )
    }

    val code = filePath.map(_.map(os.read))

    val currentSyntaxTree = code.map(_.flatMap(SyntaxTree.from))

    currentSyntaxTree.flatMap {
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

      case Success(st) => {
        val prevFilePath = os.pwd / ".pragma" / "prev"
        val prevTreeExists = os.exists(prevFilePath)

        val prevSyntaxTree: IO[SyntaxTree] =
          for {
            currentCode <- code.map(_.get)
            prevTree = if (prevTreeExists)
              SyntaxTree.from(os.read(prevFilePath)).get
            else
              SyntaxTree.empty
            migrationResult = migrationEngine(prevTree, st)
              .use(_.migrate)
              .map(_.toTry)
            _ <- migrationResult flatMap {
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
          } yield prevTree

        val storageIO = prevSyntaxTree.map(ptree => storage(ptree, st))
        val server = storageIO.map(s => new Server(s, st))

        server.flatMap(_.run(args))
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
