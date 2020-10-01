package setup.server

import running.storage._, setup.schemaGenerator.ApiSchemaGenerator
import pragma.domain._, DomainImplicits._
import pragma.domain.utils.UserError
import cats.Monad

import scala.util._, scala.io.StdIn
import pragma.domain._, pragma.domain.utils._
import org.parboiled2.Position
import running._
import org.parboiled2.ParseError
import cats.effect._, cats.implicits._
import doobie._, doobie.hikari._
import running.storage.postgres._
import org.http4s.HttpRoutes
import cats.effect._
import org.http4s._, org.http4s.dsl.io._
import org.http4s.server.blaze._, org.http4s.server.Router
import org.http4s.util._, org.http4s.implicits._, org.http4s.server.middleware._
import scala.concurrent.ExecutionContext.global
import spray.json._
import setup.server.implicits._
import collection.mutable.{Map => MutMap}

object SetupServer extends IOApp {

  type ProjectId = Int

  val projectServers: MutMap[ProjectId, Server] = MutMap.empty

  def routes(db: DaemonDB) = CORS {
    GZip {
      HttpRoutes.of[IO] {
        // Setup phase
        case req @ POST -> Root / "project" / "create" => {
          val project = req.bodyText.map(_.parseJson.convertTo[ProjectInput])
          val res =
            project
              .map(project => db.runQuery(db.createProject(project)))
              .compile
              .toVector
              .map(_.sequence)
              .flatten
              .void

          res.map { _ =>
            Response[IO](
              Status.Ok,
              HttpVersion.`HTTP/1.1`,
              Headers(List(Header("Content-Type", "application/json")))
            )
          }
        }
        case req @ POST -> Root / "project" / "migrate"                   => ???
        case req @ GET -> Root / "project" / "start-server" / projectId   => ???
        case req @ GET -> Root / "project" / "restart-server" / projectId => ???
      }
    }
  }

  override def run(args: List[String]): IO[ExitCode] = {
    val DAEMON_PG_USER = sys.env.get("DAEMON_PG_USER")
    val DAEMON_PG_URI = sys.env.get("DAEMON_PG_URI")
    val DAEMON_PG_PASSWORD = sys.env.get("DAEMON_PG_PASSWORD")

    val missingEnvVars =
      List(
        ("DAEMON_PG_URI", DAEMON_PG_URI, "Your PostgreSQL DB URL"),
        ("DAEMON_PG_USER", DAEMON_PG_USER, "Your PostgreSQL DB username"),
        (
          "DAEMON_PG_PASSWORD",
          DAEMON_PG_PASSWORD,
          "Your PostgreSQL DB password"
        )
      ).collect {
        case (name, None, desc) => name -> desc
      }.toList

    val transactor = (DAEMON_PG_USER, DAEMON_PG_URI, DAEMON_PG_PASSWORD) match {
      case (Some(pgUser), Some(pgUri), Some(pgPassword)) => {
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
        IO(t)
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

    for {
      t <- transactor
      db <- t.use(tx => IO(new DaemonDB(tx)))
      runServer <- BlazeServerBuilder[IO](global)
        .bindHttp(3030, "localhost")
        .withHttpApp(Router("/" -> routes(db)).orNotFound)
        .serve
        .compile
        .drain
        .as(ExitCode.Success)
    } yield runServer
  }
}
