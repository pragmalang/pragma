package setup.server

import running._
import cats.implicits._
import doobie._, doobie.hikari._
import org.http4s.HttpRoutes
import cats.effect._
import org.http4s._, org.http4s.dsl.io._
import org.http4s.server.blaze._, org.http4s.server.Router
import org.http4s.implicits._, org.http4s.server.middleware._
import scala.concurrent.ExecutionContext.global
import spray.json._
import setup.server.DaemonJsonProtocol._
import collection.mutable.{Map => MutMap}
import pragma.domain._
import running.storage.postgres._
import running.PFunctionExecutor
import scala.util.Success
import scala.util.Failure

object SetupServer extends IOApp {

  type ProjectId = String

  val projectServers: MutMap[ProjectId, Server] = MutMap.empty

  def removeAllTablesFromDb(transactor: HikariTransactor[IO]): IO[Unit] =
    transactor.trans.apply {
      Fragment(
        s"""|DROP SCHEMA public CASCADE;
            |CREATE SCHEMA public;
            |GRANT ALL ON SCHEMA public TO ${transactor.kernel.getUsername};
            |GRANT ALL ON SCHEMA public TO public;
            |""".stripMargin,
        Nil
      ).update.run
    }.void

  def buildTransactor(
      uri: String,
      username: String,
      password: String
  ): Resource[IO, HikariTransactor[IO]] = {
    for {
      exCtx <- ExecutionContexts.fixedThreadPool[IO](
        Runtime.getRuntime.availableProcessors * 10
      )
      blocker <- Blocker[IO]
      transactor <- HikariTransactor.newHikariTransactor[IO](
        "org.postgresql.Driver",
        if (uri.startsWith("postgresql://"))
          s"jdbc:$uri"
        else
          s"jdbc:postgresql://$uri",
        username,
        password,
        exCtx,
        blocker
      )
    } yield transactor
  }

  def buildMigrationEngine(
      prevTree: SyntaxTree,
      currentTree: SyntaxTree,
      transactor: Resource[IO, HikariTransactor[IO]],
      queryEngine: PostgresQueryEngine[IO],
      funcExecutor: PFunctionExecutor[IO]
  ): Resource[IO, PostgresMigrationEngine[IO]] =
    transactor map { t =>
      new PostgresMigrationEngine[IO](
        t,
        prevTree,
        currentTree,
        queryEngine,
        funcExecutor
      )
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
      jc: JwtCodec,
      funcExecutor: PFunctionExecutor[IO]
  ): Resource[IO, Postgres[IO]] =
    for {
      qe <- buildQueryEngine(currentTree, transactor, jc)
      me <- buildMigrationEngine(
        prevTree,
        currentTree,
        transactor,
        qe,
        funcExecutor
      )
    } yield new Postgres[IO](me, qe)

  def bodyStream(s: String) =
    fs2.Stream.fromIterator[IO](s.getBytes().iterator)

  def response(status: Status, body: Option[JsValue] = None) =
    Response[IO](
      status,
      HttpVersion.`HTTP/1.1`,
      Headers(List(Header("Content-Type", "application/json"))),
      body = body match {
        case Some(body) => bodyStream(body.prettyPrint)
        case None       => bodyStream("")
      }
    )

  def migrate(
      projectName: String,
      migration: MigrationInput,
      wskApiHost: Uri,
      wskAuthToken: String,
      wskApiVersion: Int,
      db: DaemonDB
  ) =
    db.getProject(projectName) flatMap { projectOption =>
      val project = projectOption match {
        case Some(value) => value
        case None =>
          throw new Exception(s"Project doesn't `$projectName` exist")
      }
      val prevSt =
        SyntaxTree.from(project.migrationHistory.last.code).get
      val currentSt =
        SyntaxTree.from(migration.code).get

      val jc = new JwtCodec(project.secret)
      val funcExecutor = new PFunctionExecutor[IO](
        WskConfig(
          wskApiVersion,
          project.name,
          wskApiHost,
          wskAuthToken
        )
      )

      val transactor = for {
        exCtx <- ExecutionContexts.fixedThreadPool[IO](
          Runtime.getRuntime.availableProcessors * 10
        )
        blocker <- Blocker[IO]
        transactor <- HikariTransactor.newHikariTransactor[IO](
          "org.postgresql.Driver",
          project.pgUri,
          project.pgUser,
          project.pgPassword,
          exCtx,
          blocker
        )
      } yield transactor

      val storage = buildStorage(
        prevSt,
        currentSt,
        transactor,
        jc,
        funcExecutor
      )
      val server = storage.use[IO, Server] { s =>
        new Server(jc, s, currentSt, funcExecutor).pure[IO]
      }

      server.map { server =>
        projectServers.addOne(project.name -> server)
      }.void
    }

  def routes(
      db: DaemonDB,
      wskApiHost: Uri,
      wskAuthToken: String,
      wskApiVersion: Int
  ) = CORS {
    GZip {
      HttpRoutes.of[IO] {
        // Setup phase
        case req @ POST -> Root / "project" / "create" => {
          val project = req.bodyText
            .map(_.parseJson.convertTo[ProjectInput])
            .compile
            .toVector
            .map(_.head)

          for {
            project <- project
            _ <- db.createProject(project)
          } yield response(Status.Ok)
        }
        case req @ POST -> Root / "project" / "migrate" / projectName => ???
        case req @ POST -> Root / "project" / projectName / "graphql" => {

          val projectServerNotFound =
            response(
              Status.NotFound,
              s"Project ${projectName} doesn't exist".toJson.some
            )
          val notFound404 = response(Status.NotFound)

          projectServers.get(projectName) match {
            case Some(server) =>
              server.handle.run(req).value.map {
                case None           => notFound404
                case Some(response) => response
              }
            case None => projectServerNotFound.pure[IO]
          }
        }
      }
    }
  }

  override def run(args: List[String]): IO[ExitCode] = {
    val DAEMON_PG_USER = sys.env.get("DAEMON_PG_USER")
    val DAEMON_PG_URI = sys.env.get("DAEMON_PG_URI")
    val DAEMON_PG_PASSWORD = sys.env.get("DAEMON_PG_PASSWORD")
    val DAEMON_WSK_API_HOST = sys.env.get("DAEMON_WSK_API_HOST")
    val DAEMON_WSK_AUTH_TOKEN = sys.env.get("DAEMON_WSK_AUTH_TOKEN")
    val DAEMON_WSK_API_VERSION = sys.env.get("DAEMON_WSK_API_VERSION")

    val missingEnvVars =
      List(
        ("DAEMON_PG_URI", DAEMON_PG_URI, "PostgreSQL DB URL"),
        ("DAEMON_PG_USER", DAEMON_PG_USER, "PostgreSQL DB username"),
        (
          "DAEMON_WSK_API_HOST",
          DAEMON_WSK_API_HOST,
          "OpenWhisk's API Host"
        ),
        (
          "DAEMON_WSK_AUTH_TOKEN",
          DAEMON_WSK_AUTH_TOKEN,
          "OpenWhisk's API Auth Token"
        ),
        (
          "DAEMON_PG_PASSWORD",
          DAEMON_PG_PASSWORD,
          "Your PostgreSQL DB password"
        ),
        (
          "DAEMON_WSK_API_VERSION",
          DAEMON_WSK_API_VERSION,
          "OpenWhisk's API version"
        )
      ).collect {
        case (name, None, desc) => name -> desc
      }.toList

    val transactorAndWskConfig = (
      DAEMON_PG_USER,
      DAEMON_PG_URI,
      DAEMON_PG_PASSWORD,
      DAEMON_WSK_API_HOST,
      DAEMON_WSK_AUTH_TOKEN,
      DAEMON_WSK_API_VERSION
    ) match {
      case (
          Some(pgUser),
          Some(pgUri),
          Some(pgPassword),
          Some(wskApiHost),
          Some(wskAuthToken),
          Some(wskApiVersion)
          ) => {
        val t = for {
          exCtx <- ExecutionContexts.fixedThreadPool[IO](
            Runtime.getRuntime.availableProcessors * 10
          )
          blocker <- Blocker[IO]
          transactor <- HikariTransactor.newHikariTransactor[IO](
            "org.postgresql.Driver",
            pgUri,
            pgUser,
            pgPassword,
            exCtx,
            blocker
          )
        } yield transactor
        IO((t, (wskApiHost, wskAuthToken, wskApiVersion)))
      }

      case _ =>
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

          println(errMsg)
          sys.exit(1)
        }
    }

    val transactor = transactorAndWskConfig.map(_._1)
    val wskApiHost = transactorAndWskConfig
      .map(_._2._1)
      .flatMap { s =>
        Uri.fromString(s) match {
          case Left(value)  => IO.raiseError(value)
          case Right(value) => value.pure[IO]
        }
      }
    val wskAuthToken = transactorAndWskConfig.map(_._2._2)
    val wskApiVersion = transactorAndWskConfig.map(_._2._3).map(_.toInt)

    for {
      t <- transactor
      db <- t.use(tx => IO(new DaemonDB(tx)))
      wskApiHost <- wskApiHost
      wskAuthToken <- wskAuthToken
      wskApiVersion <- wskApiVersion
      runServer <- BlazeServerBuilder[IO](global)
        .bindHttp(3030, "localhost")
        .withHttpApp(
          Router("/" -> routes(db, wskApiHost, wskAuthToken, wskApiVersion)).orNotFound
        )
        .serve
        .compile
        .drain
        .as(ExitCode.Success)
    } yield runServer
  }
}
