package daemon.server

import running._, running.storage.postgres._
import running.utils._, running.PFunctionExecutor
import pragma.domain._
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
import daemon.utils._
import pragma.utils.JsonCodec._
import running.RunningImplicits._
import metacall.{Caller, Runtime => MCRuntime}
import java.nio.file.Paths
import running.utils.Mode.Dev
import running.utils.Mode.Prod

class Server(port: Int, hostname: String, dbInfo: DBInfo, secret: String)(implicit
    cs: ContextShift[IO],
    timer: Timer[IO]
) {

  type ProjectId = String

  val devProjectServers: MutMap[ProjectId, GraphQLServer] = MutMap.empty
  val prodProjectServers: MutMap[ProjectId, GraphQLServer] = MutMap.empty

  val devProjectTransactors: MutMap[ProjectId, HikariTransactor[IO]] = MutMap.empty
  val prodProjectTransactors: MutMap[ProjectId, HikariTransactor[IO]] = MutMap.empty

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
      Runtime.getRuntime.availableProcessors * 100
    )
    .allocated
    .unsafeRunSync()

  val (blocker, _) = Blocker[IO].allocated.unsafeRunSync()

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

  private def migrate(
      projectName: String,
      code: String,
      resolutionPath: String,
      mode: Mode
  ): IO[Response[IO]] = {
    val currentSt =
      SyntaxTree.from(code).get

    val jc = new JwtCodec(secret)

    val funcExecutor = new PFunctionExecutor[IO]

    val pgUri = jdbcPostgresUri(
      dbInfo.host,
      dbInfo.port,
      dbInfo.dbName
    )

    // TODO: Move this parsing logic to Substituter/Validator
    // WARNNING!!!: REMOVE THIS CODE WHEN WE START WRITING THE CLOUD SERVICE
    val loadFunctionFiles = currentSt.imports.toList.traverse { i =>
      val checkRuntime = (e: ConfigEntry, runtimes: List[String]) =>
        e.value match {
          case PStringValue(value) =>
            e.key == "runtime" && runtimes.contains(value)
          case _ => false
        }
      val isNode = (e: ConfigEntry) => checkRuntime(e, "node" :: "nodejs" :: Nil)
      val isPython = (e: ConfigEntry) => checkRuntime(e, "python" :: "python3" :: Nil)
      val runtime = i.config.flatMap(_.values.find(_.key == "runtime"))
      val filePath = Paths.get(resolutionPath + "/" + i.filePath).toString()

      runtime match {
        case Some(runtime) if isNode(runtime) =>
          IO.fromFuture(IO(Caller.loadFile(MCRuntime.Node, filePath, i.id)))
        case Some(runtime) if isPython(runtime) =>
          IO.fromFuture(IO(Caller.loadFile(MCRuntime.Python, filePath, i.id)))
        case Some(runtime) =>
          IO.raiseError {
            new Exception(
              s"Invalid runtime `${pragma.domain.utils
                .displayPValue(runtime.value)}` in import `${i.id}` for path `${i.filePath}`"
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

    val projectTransactors = mode match {
      case Dev  => devProjectTransactors
      case Prod => prodProjectTransactors
    }

    val transactor = IO {
      projectTransactors.getOrElseUpdate(
        projectName,
        HikariTransactor
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
      )
    }

    val res = for {
      // TODO: Unload previously loaded function files (scripts) before loading the new ones
      transactor <- transactor
      storage = buildStorage(
        currentSt,
        transactor,
        jc,
        funcExecutor
      )
      server = new GraphQLServer(jc, storage, currentSt, funcExecutor)
      _ <- loadFunctionFiles
      _ <- mode match {
        case Mode.Dev =>
          removeAllTablesFromDb(transactor) *>
            storage.migrate(mode, code)
        case Mode.Prod =>
          storage.migrate(mode, code)
      }
      _ <- mode match {
        case Mode.Dev =>
          devProjectServers.addOne(projectName -> server).pure[IO]
        case Mode.Prod =>
          prodProjectServers.addOne(projectName -> server).pure[IO]
      }
    } yield response(Status.Ok)

    res.handleError { err =>
      response(Status.BadRequest, err.getMessage.toJson.some)
    }
  }

  private def routes =
    HttpRoutes.of[IO] {
      case req @ POST -> Root / "project" / "migrate" / modeStr / projectName => {
        val mode: IO[Mode] = modeStr match {
          case "dev"  => IO(Mode.Dev)
          case "prod" => IO(Mode.Prod)
          case _      => IO.raiseError(new Exception("Invalid mode route"))
        }

        val body = req.bodyText.compile.string
          .map(bodyText => {
            bodyText.parseJson.asJsObject
          })

        val code = body.map(_.fields("code").asInstanceOf[JsString].value)
        val resolutionPath =
          body.map(_.fields("resolutionPath").asInstanceOf[JsString].value)

        for {
          mode <- mode
          code <- code
          resolutionPath <- resolutionPath
          response <- migrate(
            projectName,
            code,
            resolutionPath,
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
      case req @ (POST | GET) ->
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
    for {
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
        .map(_ => ExitCode.Success)
    } yield runServer
  }

}

case class DBInfo(
    host: String,
    port: String,
    user: String,
    password: String,
    dbName: String
) {
  def jdbcPostgresUri: String = daemon.utils.jdbcPostgresUri(host, port, dbName)
}
