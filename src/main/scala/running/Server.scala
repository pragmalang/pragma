package running

import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import scala.concurrent.ExecutionContext.Implicits.global
import org.http4s.server.blaze._
import org.http4s.server.Router
import fs2._
import org.http4s.implicits._
import assets._
import cats.implicits._
import sangria.schema.Schema
import sangria.execution.Executor
import sangria.parser.QueryParser
import sangria.marshalling.sprayJson._
import spray.json._
import scala.concurrent._
import scala.concurrent.duration._
import domain.SyntaxTree
import setup.schemaGenerator.ApiSchemaGenerator

class Server(st: SyntaxTree) extends IOApp {

  val routes = HttpRoutes.of[IO] {
    case req @ POST -> Root => {
      val res: Stream[IO, Response[IO]] = req.bodyAsText map { body =>
        val jsonBody = body.parseJson.asJsObject
        if (jsonBody.fields("operationName") == JsString("IntrospectionQuery")) {
          Response[IO](
            Status(200),
            HttpVersion(1, 1),
            Headers(List(Header("Content-Type", "application/json"))),
            body = Stream.fromIterator[IO](introspectionResult(jsonBody))
          )
        } else {
          Response.notFound
        }
      }
      res.compile.toVector.map(_.head)
    }
    case GET -> Root =>
      Response[IO](
        Status(200),
        HttpVersion(1, 1),
        Headers(List(Header("Content-Type", "text/html"))),
        body = Stream.fromIterator[IO](playgroundHtml.getBytes().iterator)
      ).pure[IO]
  }

  def run(args: List[String]): IO[ExitCode] =
    BlazeServerBuilder[IO](global)
      .bindHttp(3030, "localhost")
      .withHttpApp(Router("/graphql" -> routes).orNotFound)
      .serve
      .compile
      .drain
      .as(ExitCode.Success)

  def introspectionResult(query: JsObject) = Await.result(
    Executor
      .execute(
        Schema.buildFromAst(ApiSchemaGenerator(st).buildApiSchemaAsDocument),
        QueryParser
          .parse(
            query
              .fields("query")
              .asInstanceOf[JsString]
              .value
          )
          .get
      )
      .map(_.compactPrint.getBytes.iterator),
    10.seconds
  )

}
