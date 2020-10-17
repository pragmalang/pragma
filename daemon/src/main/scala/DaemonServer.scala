import running._, running.storage.postgres._
import running.utils._, running.PFunctionExecutor
import pragma.daemonProtocol._, pragma.domain._, DaemonJsonProtocol._
import cats.implicits._, cats.effect._
import doobie._, doobie.hikari._
import scala.concurrent.ExecutionContext
import ExecutionContext.global, scala.concurrent.duration._
import spray.json._
import collection.mutable.{Map => MutMap}
import java.nio.charset.StandardCharsets.UTF_8
import org.http4s._, org.http4s.dsl.io._, org.http4s.implicits._
import org.http4s.server.middleware._
import org.http4s.server.blaze._, org.http4s.server.Router
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.headers.Authorization
import org.postgresql.util.PSQLException
import pragma.envUtils._

object DeamonServer extends IOApp {

  type ProjectId = String

  val devProjectServers: MutMap[ProjectId, Server] = MutMap.empty
  val prodProjectServers: MutMap[ProjectId, Server] = MutMap.empty

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

  val executionContext = ExecutionContexts.fixedThreadPool[IO](
    Runtime.getRuntime.availableProcessors * 10
  )

  val blocker = Blocker[IO]

  def buildTransactor(
      uri: String,
      username: String,
      password: String
  ): Resource[IO, HikariTransactor[IO]] = {
    for {
      exCtx <- executionContext
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
      currentTree: SyntaxTree,
      transactor: Resource[IO, HikariTransactor[IO]],
      queryEngine: PostgresQueryEngine[IO],
      funcExecutor: PFunctionExecutor[IO]
  ): Resource[IO, PostgresMigrationEngine[IO]] =
    transactor map { t =>
      new PostgresMigrationEngine[IO](
        t,
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
      currentTree: SyntaxTree,
      transactor: Resource[IO, HikariTransactor[IO]],
      jc: JwtCodec,
      funcExecutor: PFunctionExecutor[IO]
  ): Resource[IO, Postgres[IO]] =
    for {
      qe <- buildQueryEngine(currentTree, transactor, jc)
      me <- buildMigrationEngine(
        currentTree,
        transactor,
        qe,
        funcExecutor
      )
    } yield new Postgres[IO](me, qe)

  def bodyStream(s: String) =
    fs2.Stream.fromIterator[IO](s.getBytes(UTF_8).iterator)

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
      daemonConfig: DaemonConfig,
      mode: Mode,
      wskClient: WskClient[IO]
  ) =
    daemonConfig.db
      .getProject(projectName)
      .flatMap { projectOption =>
        val project = projectOption match {
          case Some(value) => value
          case None =>
            throw new Exception(s"Project `$projectName` doesn't exist")
        }

        val currentSt =
          SyntaxTree.from(migration.code).get

        val jc = new JwtCodec(project.secret)
        val funcExecutor = new PFunctionExecutor[IO](
          project.name,
          wskClient
        )

        val transactor = HikariTransactor.newHikariTransactor[IO](
          "org.postgresql.Driver",
          project.pgUri,
          project.pgUser,
          project.pgPassword,
          daemonConfig.execCtx,
          daemonConfig.blocker
        )

        val storageWithTransactor = buildStorage(
          currentSt,
          transactor,
          jc,
          funcExecutor
        ).flatMap(s => transactor.map(s -> _))

        val server = storageWithTransactor.use[IO, Server] { s =>
          val storage = s._1
          val transactor = s._2
          val removeAllTables = removeAllTablesFromDb(transactor)
          val persistPrevMigration =
            daemonConfig.db.persistPreviousMigration(projectName, migration)
          val migrate = storage.migrate(mode, migration.code)

          val createWskActions: IO[Unit] =
            migration.functions.traverse { function =>
              wskClient.createAction(
                function.name,
                function.content,
                function.runtime,
                function.binary,
                projectName
              )
            }.void

          for {
            _ <- mode match {
              case Mode.Dev  => removeAllTables *> migrate
              case Mode.Prod => migrate *> persistPrevMigration
            }
            _ = println(s"Migrated `$projectName`")
            _ <- createWskActions
            _ = println(s"Created `$projectName`'s functions'")
          } yield new Server(jc, storage, currentSt, funcExecutor)
        }

        server
          .map { server =>
            mode match {
              case Mode.Dev  => devProjectServers.addOne(project.name -> server)
              case Mode.Prod => prodProjectServers.addOne(project.name -> server)
            }
          }
          .map(_ => response(Status.Ok))
      }
      .handleError(
        err => response(Status.BadRequest, err.getMessage().toJson.some)
      )

  def parseBody[A: JsonReader](req: org.http4s.Request[IO]) =
    req.bodyText
      .map(_.parseJson.convertTo[A])
      .compile
      .toVector
      .map(_.head)

  def routes(daemonConfig: DaemonConfig, wskClient: WskClient[IO]) =
    HttpRoutes.of[IO] {
      // Setup phase
      case req @ POST -> Root / "project" / "create" => {
        val project = parseBody[ProjectInput](req)
        val res = for {
          project <- project
          _ <- daemonConfig.db.createProject(project)
        } yield ()

        res.as(response(Status.Ok)).handleErrorWith {
          case e: PSQLException if e.getSQLState == "23505" =>
            project.map { project =>
              response(
                Status.BadRequest,
                s"Failed to create project: Project ${project.name} already exists".toJson.some
              )
            }
          case e =>
            response(
              Status.InternalServerError,
              s"Failed to create project: ${e.getMessage}".toJson.some
            ).pure[IO]
        }
      }
      case req @ POST -> Root / "project" / "migrate" / modeStr / projectName => {
        val mode: IO[Mode] = modeStr match {
          case "dev"  => IO(Mode.Dev)
          case "prod" => IO(Mode.Prod)
          case _      => IO.raiseError(new Exception("Invalid mode route"))
        }

        val migration = parseBody[MigrationInput](req)

        for {
          mode <- mode
          migration <- migration
          response <- migrate(
            projectName,
            migration,
            daemonConfig,
            mode,
            wskClient
          )
        } yield response
      }
      case req @ (POST |
          GET) -> Root / "project" / projectName / mode / "graphql" => {
        val servers = mode match {
          case "dev"  => Some(devProjectServers)
          case "prod" => Some(prodProjectServers)
          case _      => None
        }

        val notFound404 = response(Status.NotFound, "Not Found".toJson.some)
        val projectNotFound404 = response(
          Status.NotFound,
          s"Project $projectName Not Found".toJson.some
        )

        servers.flatMap(_.get(projectName)) match {
          case Some(server) =>
            Router(req.uri.renderString -> server.routes).run(req).value.map {
              case Some(v) => v
              case None    => notFound404
            }
          case None => projectNotFound404.pure[IO]
        }
      }
      case GET -> Root / "ping" => response(Status.Ok).pure[IO]
    }

  override def run(args: List[String]): IO[ExitCode] =
    (executionContext, blocker).bisequence.use { ctx =>
      val (execCtx, blocker) = ctx

      val getEnvVar = EnvVarDef.parseEnvVars(DaemonServer.envVarsDefs) match {
        case Left(errors) =>
          IO {
            print(EnvVarError.render(errors))
            sys.exit(1)
          }
        case Right(getter) => getter.pure[IO]
      }

      val pgHost = getEnvVar
        .map(getter => getter(DaemonServer.DAEMON_PG_HOST))

      val pgPort = getEnvVar
        .map(getter => getter(DaemonServer.DAEMON_PG_PORT))

      val pgDbName = getEnvVar
        .map(getter => getter(DaemonServer.DAEMON_PG_DB_NAME))

      val pgUser = getEnvVar
        .map(getter => getter(DaemonServer.DAEMON_PG_USER))

      val pgPassword = getEnvVar
        .map(getter => getter(DaemonServer.DAEMON_PG_PASSWORD))

      val wskApiUrl = getEnvVar
        .map(getter => getter(DaemonServer.DAEMON_WSK_API_URL))
        .flatMap { s =>
          Uri.fromString(s) match {
            case Left(value)  => IO.raiseError(value)
            case Right(value) => value.pure[IO]
          }
        }

      val wskAuthToken =
        getEnvVar
          .map(getter => getter(DaemonServer.DAEMON_WSK_AUTH_TOKEN))
          .map(_.split(":").toList)
          .flatMap {
            case user :: pw :: Nil => BasicCredentials(user, pw).pure[IO]
            case _ =>
              IO.raiseError {
                new IllegalArgumentException(
                  "Invalid OpenWhisk auth: must consist of a username followed by ':' and a password"
                )
              }
          }

      val wskApiVersion = getEnvVar
        .map(getter => getter(DaemonServer.DAEMON_WSK_API_VERSION).toInt)

      val hostname = getEnvVar
        .map(getter => getter(DaemonServer.DAEMON_HOSTNAME))

      val port = getEnvVar
        .map(getter => getter(DaemonServer.DAEMON_PORT).toInt)

      val transactor = for {
        pgHost <- pgHost
        pgPort <- pgPort
        pgDbName <- pgDbName
        pgUser <- pgUser
        pgPassword <- pgPassword
      } yield
        HikariTransactor.newHikariTransactor[IO](
          "org.postgresql.Driver",
          s"jdbc:postgresql://$pgHost:$pgPort/$pgDbName",
          pgUser,
          pgPassword,
          execCtx,
          blocker
        )

      val wskConfig = for {
        wskApiUrl <- wskApiUrl
        wskAuthToken <- wskAuthToken
        wskApiVersion <- wskApiVersion
      } yield WskConfig(wskApiVersion, wskApiUrl, wskAuthToken)

      val clientResource = BlazeClientBuilder[IO](execCtx).resource

      for {
        t <- transactor
        db = new DaemonDB(t)
        _ <- db.migrate
        wskConfig <- wskConfig
        wskClientResource = clientResource.map[IO, WskClient[IO]] { c =>
          new WskClient(wskConfig, c)
        }
        _ <- IO {
          println(s"Pinging OpenWhisk at ${wskConfig.wskApiUrl.renderString}")
        }
        _ <- clientResource.use(client => pingWsk(client, wskConfig))
        hostname <- hostname
        port <- port
        daemonConfig = DaemonConfig(wskConfig, execCtx, blocker, db)
        runServer <- wskClientResource.use { wsk =>
          BlazeServerBuilder[IO](global)
            .bindHttp(port, hostname)
            .withHttpApp {
              Router(
                "/" -> GZip(routes(daemonConfig, wsk))
              ).orNotFound
            }
            .serve
            .compile
            .drain
            .as(ExitCode.Success)
        }
      } yield runServer
    }

  def pingWsk(
      client: org.http4s.client.Client[IO],
      wskConfig: WskConfig,
      retries: Int = 5
  ): IO[Unit] = {
    val request = org.http4s
      .Request[IO]()
      .withMethod(GET)
      .withUri(
        Uri
          .fromString(
            s"${wskConfig.wskApiUrl.renderString}/api/v${wskConfig.wskApiVersion}"
          )
          .toTry
          .get
      )
      .withHeaders(
        Authorization(wskConfig.wskAuthToken),
        Header("Accept", "application/json")
      )

    for {
      isSuccess <- client.successful(request).handleError(_ => false)
      _ <- {
        if (isSuccess)
          println("Ping successful").pure[IO]
        else {
          if (retries > 0)
            IO.sleep(3.seconds) *> pingWsk(client, wskConfig, retries - 1)
          else
            IO {
              println(s"Unable to connect to OpenWhisk... Abborting")
              sys.exit(1)
            }
        }
      }
    } yield ()
  }

}
object DaemonServer {
  val DAEMON_PG_USER = EnvVarDef(
    name = "DAEMON_PG_USER",
    description = "PostgreSQL DB username",
    isRequired = true
  )
  val DAEMON_PG_PASSWORD = EnvVarDef(
    name = "DAEMON_PG_PASSWORD",
    description = "PostgreSQL DB password",
    isRequired = true
  )
  val DAEMON_PG_HOST = EnvVarDef(
    name = "DAEMON_PG_HOST",
    description = "PostgreSQL DB host",
    isRequired = true
  )
  val DAEMON_PG_PORT = EnvVarDef(
    name = "DAEMON_PG_PORT",
    description = "PostgreSQL DB port",
    isRequired = true
  )
  val DAEMON_PG_DB_NAME = EnvVarDef(
    name = "DAEMON_PG_DB_NAME",
    description = "PostgreSQL DB name",
    isRequired = true
  )
  val DAEMON_WSK_API_URL = EnvVarDef(
    name = "DAEMON_WSK_API_URL",
    description = "OpenWhisk API URL",
    isRequired = true
  )
  val DAEMON_WSK_API_VERSION = EnvVarDef(
    name = "DAEMON_WSK_API_VERSION",
    description = "OpenWhisk's API version",
    isRequired = true
  )
  val DAEMON_WSK_AUTH_TOKEN = EnvVarDef(
    name = "DAEMON_WSK_AUTH_TOKEN",
    description = "OpenWhisk's auth token (in the form <user>:<password>)",
    isRequired = true
  )
  val DAEMON_HOSTNAME = EnvVarDef(
    name = "DAEMON_HOSTNAME",
    description = "Pragma Daemon hostname",
    isRequired = true,
    defaultValue = "localhost".some
  )
  val DAEMON_PORT = EnvVarDef(
    name = "DAEMON_PORT",
    description = "Pragma Daemon port",
    isRequired = true,
    defaultValue = "3030".some
  )

  val envVarsDefs = List(
    DAEMON_PG_USER,
    DAEMON_PG_PASSWORD,
    DAEMON_PG_HOST,
    DAEMON_PG_PORT,
    DAEMON_PG_DB_NAME,
    DAEMON_WSK_API_URL,
    DAEMON_WSK_API_VERSION,
    DAEMON_WSK_AUTH_TOKEN,
    DAEMON_HOSTNAME,
    DAEMON_PORT
  )
}

case class DaemonConfig(
    wskConfig: WskConfig,
    execCtx: ExecutionContext,
    blocker: Blocker,
    db: DaemonDB
)
