import running._, running.storage.postgres._
import running.utils._, running.PFunctionExecutor
import pragma.daemonProtocol._, pragma.domain._
import pragma.jwtUtils._
import cats.implicits._, cats.effect._
import doobie._, doobie.hikari._
import scala.concurrent.ExecutionContext
import ExecutionContext.global
import spray.json._
import collection.mutable.{Map => MutMap}
import java.nio.charset.StandardCharsets.UTF_8
import org.http4s._, org.http4s.dsl.io._, org.http4s.implicits._
import org.http4s.server.middleware._
import org.http4s.server.blaze._, org.http4s.server.Router
import org.postgresql.util.PSQLException
import pragma.envUtils._
import java.sql._
import daemon.utils._
import pragma.utils.JsonCodec._
import running.utils.Mode.Dev
import running.utils.Mode.Prod
import running.RunningImplicits._
import metacall.{Caller, Runtime => MCRuntime}

class Server(dbInfo: DBInfo)(implicit cs: ContextShift[IO], timer: Timer[IO]) {

  type ProjectId = String

  val devProjectServers: MutMap[ProjectId, GraphQLServer] = MutMap.empty
  val prodProjectServers: MutMap[ProjectId, GraphQLServer] = MutMap.empty

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

  val (executionContext, _) = ExecutionContexts
    .fixedThreadPool[IO](
      Runtime.getRuntime.availableProcessors * 10
    )
    .allocated
    .unsafeRunSync()

  val (blocker, _) = Blocker[IO].allocated.unsafeRunSync()

  def createDatabase(
      host: String,
      port: String,
      username: String,
      password: String,
      dbName: String
  ) = IO {
    val connection = DriverManager
      .getConnection(jdbcPostgresUri(host, port), username, password)
    val statement = connection.createStatement()
    statement.executeUpdate(s"CREATE DATABASE $dbName;")
    connection.close()
  }

  private def buildStorage(
      currentTree: SyntaxTree,
      transactor: HikariTransactor[IO],
      jc: JwtCodec,
      funcExecutor: PFunctionExecutor[IO]
  ) = {
    val qe = new PostgresQueryEngine[IO](transactor, currentTree, jc)
    val me =
      new PostgresMigrationEngine[IO](transactor, currentTree, qe, funcExecutor)
    new Postgres[IO](me, qe)
  }

  private def bodyStream(s: String) =
    fs2.Stream.fromIterator[IO](s.getBytes(UTF_8).iterator)

  private def response(status: Status, body: Option[JsValue] = None) =
    Response[IO](
      status,
      HttpVersion.`HTTP/1.1`,
      Headers(List(Header("Content-Type", "application/json"))),
      body = body match {
        case Some(body) => bodyStream(body.prettyPrint)
        case None       => bodyStream("")
      }
    )

  private def dbName(projectName: String, mode: Mode): String = mode match {
    case Dev  => s"${projectName}_dev"
    case Prod => s"${projectName}_prod"
  }

  private def migrate(
      projectName: String,
      migration: MigrationInput,
      mode: Mode
  ): IO[Response[IO]] = {
    val currentSt =
      SyntaxTree.from(migration.code).get

    val jc = new JwtCodec(migration.secret)

    val funcExecutor = new PFunctionExecutor[IO]

    val pgUri = jdbcPostgresUri(
      dbInfo.host,
      dbInfo.port,
      dbName(projectName, mode).some
    )

    // TODO: Move this parsing logic to Substituter/Validator
    val loadFunctionFiles = currentSt.imports.toList.traverse { i =>
      val checkRuntime = (e: ConfigEntry, runtimes: List[String]) =>
        e.key == "runtime" && runtimes.contains(e.value)
      val isNode = (e: ConfigEntry) => checkRuntime(e, "node" :: "nodejs" :: Nil)
      val isPython = (e: ConfigEntry) => checkRuntime(e, "python" :: "python3" :: Nil)
      val runtime = i.config.flatMap(_.values.find(_.key == "runtime"))
      runtime match {
        case Some(runtime) if isNode(runtime) =>
          IO.fromFuture(IO(Caller.loadFile(MCRuntime.Node, i.filePath, i.id)))
        case Some(runtime) if isPython(runtime) =>
          IO.fromFuture(IO(Caller.loadFile(MCRuntime.Python, i.filePath, i.id)))
        case Some(runtime) =>
          IO.raiseError {
            new Exception(
              s"Invalid runtime `${runtime.value}` in import `${i.id}` for path `${i.filePath}`"
            )
          }
        case None =>
          IO.raiseError {
            new Exception(
              s"""|Runtime was not specified for import `${i.id}` for path `${i.filePath}`
                  |Example:
                  |import "${i.filePath}" as ${i.id} { runtime = "nodejs" } 
                  |""".stripMargin
            )
          }
      }
    }.void

    val pgUser = dbInfo.user

    val pgPassword = dbInfo.password

    val transactor = HikariTransactor
      .newHikariTransactor[IO](
        "org.postgresql.Driver",
        pgUri,
        pgUser,
        pgPassword,
        executionContext,
        blocker
      )
      .allocated
      .unsafeRunSync()
      ._1

    val storage = buildStorage(
      currentSt,
      transactor,
      jc,
      funcExecutor
    )

    val migrate = mode match {
      case Mode.Dev =>
        removeAllTablesFromDb(transactor) *>
          storage.migrate(mode, migration.code)
      case Mode.Prod =>
        storage.migrate(mode, migration.code)
    }

    val server = new GraphQLServer(jc, storage, currentSt, funcExecutor)

    val addServerToMap = mode match {
      case Mode.Dev =>
        devProjectServers.addOne(projectName -> server).pure[IO]
      case Mode.Prod =>
        prodProjectServers.addOne(projectName -> server).pure[IO]
    }

    val res = for {
      // TODO: Unload previously loaded function files (scripts) before loading the new ones
      _ <- loadFunctionFiles
      _ <- migrate
      _ <- IO(
        println(
          s"Successfully created ${projectName}'s OpenWhisk actions"
        )
      )
      _ <- addServerToMap
    } yield response(Status.Ok)

    res.handleError { err =>
      response(Status.BadRequest, err.getMessage.toJson.some)
    }
  }

  private def routes =
    HttpRoutes.of[IO] {
      // Setup phase
      case req @ POST -> Root / "project" / "create" / modeStr => {
        val parsedMode: IO[Mode] = modeStr match {
          case "dev"  => IO(Mode.Dev)
          case "prod" => IO(Mode.Prod)
          case _      => IO.raiseError(new Exception("Invalid mode route"))
        }

        val project = req.bodyText.compile.string
          .map(_.parseJson.convertTo[ProjectInput])

        val createProjectDb = for {
          mode <- parsedMode
          project <- project
          _ <- (project.pgUri, project.pgUser, project.pgPassword) match {
            case (None, None, None) => {
              val DBInfo(host, port, user, password, _) = dbInfo
              createDatabase(host, port, user, password, dbName(project.name, mode))
            }
            case _ => IO.unit
          }
        } yield ()

        createProjectDb.as(response(Status.Ok)).handleErrorWith {
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

        val migration = req.bodyText.compile.string
          .map(_.parseJson.convertTo[MigrationInput])

        for {
          mode <- mode
          migration <- migration
          response <- migrate(
            projectName,
            migration,
            mode
          )
        } yield response
      }
      case req @ (POST | GET) ->
          Root / "project" / projectName / "dev" / "graphql" => {
        devProjectServers.get(projectName) match {
          case Some(server) =>
            Router(req.uri.renderString -> server.routes).run(req).value.map {
              case Some(res) => res
              case None =>
                response(
                  Status.NotFound,
                  s"${req.uri.renderString} not found".toJson.some
                )
            }
          case None =>
            response(
              Status.NotFound,
              s"Server for project `$projectName` not found".toJson.some
            ).pure[IO]
        }
      }
      case req @ POST ->
          Root / "project" / projectName / "prod" / "graphql" => {
        prodProjectServers.get(projectName) match {
          case Some(server) =>
            Router(req.uri.renderString -> server.routes).run(req).value.map {
              case Some(res) => res
              case None =>
                response(
                  Status.NotFound,
                  s"${req.uri.renderString} not found".toJson.some
                )
            }
          case None =>
            response(
              Status.NotFound,
              s"Server for project `$projectName` not found".toJson.some
            ).pure[IO]
        }
      }
      case GET -> Root / "ping" =>
        response(Status.Ok, Some(JsString("Healthy!"))).pure[IO]
    }

  def run: IO[ExitCode] = {
    val getEnvVar = EnvVarDef.parseEnvVars(DaemonServer.envVarsDefs) match {
      case Left(errors) =>
        IO {
          print(EnvVarError.render(errors))
          sys.exit(1)
        }
      case Right(getter) => getter.pure[IO]
    }

    val hostname = getEnvVar
      .map(getter => getter(DaemonServer.PRAGMA_HOSTNAME))

    val port = getEnvVar
      .map(getter => getter(DaemonServer.PRAGMA_PORT).toInt)

    for {
      hostname <- hostname
      port <- port
      runServer <- BlazeServerBuilder[IO](global)
        .bindHttp(port, hostname)
        .withHttpApp {
          Router(
            "/" -> GZip(routes)
          ).orNotFound
        }
        .serve
        .compile
        .drain
        .as(ExitCode.Success)
    } yield runServer
  }

}
object DaemonServer {
  val PRAGMA_PG_USER = EnvVarDef(
    name = "PRAGMA_PG_USER",
    description = "PostgreSQL DB username",
    isRequired = true
  )
  val PRAGMA_PG_PASSWORD = EnvVarDef(
    name = "PRAGMA_PG_PASSWORD",
    description = "PostgreSQL DB password",
    isRequired = true
  )
  val PRAGMA_PG_HOST = EnvVarDef(
    name = "PRAGMA_PG_HOST",
    description = "PostgreSQL DB host",
    isRequired = true
  )
  val PRAGMA_PG_PORT = EnvVarDef(
    name = "PRAGMA_PG_PORT",
    description = "PostgreSQL DB port",
    isRequired = true
  )
  val PRAGMA_PG_DB_NAME = EnvVarDef(
    name = "PRAGMA_PG_DB_NAME",
    description = "PostgreSQL DB name",
    isRequired = true
  )

  val PRAGMA_HOSTNAME = EnvVarDef(
    name = "PRAGMA_HOSTNAME",
    description = "Pragma Daemon hostname",
    isRequired = true,
    defaultValue = "localhost".some
  )
  val PRAGMA_PORT = EnvVarDef(
    name = "PRAGMA_PORT",
    description = "Pragma Daemon port",
    isRequired = true,
    defaultValue = "3030".some
  )

  val envVarsDefs = List(
    PRAGMA_PG_USER,
    PRAGMA_PG_PASSWORD,
    PRAGMA_PG_HOST,
    PRAGMA_PG_PORT,
    PRAGMA_PG_DB_NAME,
    PRAGMA_HOSTNAME,
    PRAGMA_PORT
  )
}

case class DBInfo(
    host: String,
    port: String,
    user: String,
    password: String,
    dbName: Option[String]
) {
  def jdbcPostgresUri: String = daemon.utils.jdbcPostgresUri(host, port, dbName)
}
