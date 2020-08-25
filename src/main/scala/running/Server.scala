package running

import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import scala.concurrent.ExecutionContext.Implicits.global
import org.http4s.server.blaze._
import org.http4s.server.Router
import org.http4s.implicits._
import org.http4s.util._
import fs2._
import cats.implicits._
import sangria.schema.Schema
import sangria.execution.Executor
import sangria.parser.QueryParser
import sangria.marshalling.sprayJson._
import spray.json._
import scala.concurrent._
import scala.concurrent.duration._
import domain._
import domain.utils._
import setup.schemaGenerator.ApiSchemaGenerator
import running.RequestHandler
import storage.postgres._

class Server(storage: Resource[IO, Postgres[IO]], currentSyntaxTree: SyntaxTree)
    extends IOApp {

  val gqlSchema =
    Schema.buildFromAst(
      ApiSchemaGenerator(currentSyntaxTree).buildApiSchemaAsDocument
    )

  val reqHandler =
    storage.map(s => new RequestHandler[Postgres[IO], IO](currentSyntaxTree, s))

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
          val preq = running.Request(
            hookData = None,
            body = Some(jsonBody),
            cookies = req.cookies.map(c => c.name -> c.content).toMap,
            hostname = req.remoteHost.getOrElse(""),
            url = "localhost:3030",
            query = jsonBody.fields("query") match {
              case JsString(q) => QueryParser.parse(q).get
              case _           => throw UserError("Invalid GraphQL query")
            },
            queryVariables = jsonBody.fields("variables") match {
              case o: JsObject => Left(o)
              case JsArray(values) =>
                Right(values.collect { case o: JsObject => o })
              case _ => throw UserError("Invalid GraphQL query variables")
            },
            user = req.headers
              .get(CaseInsensitiveString("Authorization"))
              .flatMap(h => running.JwtPaylod.decode(h.value).toOption)
          )
          val resJson = reqHandler
            .use(rh => rh.handle(preq))
            .map[JsValue] {
              case Left(UserError(errors)) =>
                JsArray(errors.map(e => JsString(e._1)).toVector)
              case Left(otherErr) => JsString(otherErr.getMessage)
              case Right(obj)     => obj
            }
          Response[IO](
            Status(200),
            HttpVersion(1, 1),
            Headers(List(Header("Content-Type", "application/json"))),
            body = Stream.evalSeq(resJson.map(_.compactPrint.getBytes.toSeq))
          )
        }
      }
      res.compile.toVector.map(_.head)
    }
    case GET -> Root =>
      Response[IO](
        Status(200),
        HttpVersion(1, 1),
        Headers(List(Header("Content-Type", "text/html"))),
        body = Stream.fromIterator[IO](assets.playgroundHtml.getBytes.iterator)
      ).pure[IO]
  }

  def run(args: List[String]): IO[ExitCode] = {
    BlazeServerBuilder[IO](global)
      .bindHttp(3030, "localhost")
      .withHttpApp(Router("/graphql" -> routes).orNotFound)
      .serve
      .compile
      .drain
      .as(ExitCode.Success)
  }

  def introspectionResult(query: JsObject) = Await.result(
    Executor
      .execute(
        gqlSchema,
        QueryParser
          .parse(
            query
              .fields("query")
              .asInstanceOf[JsString]
              .value
          )
          .get
      )
      .map { res =>
        val typesWithEventEnum = res.asJsObject
          .fields("data")
          .asJsObject
          .fields("__schema")
          .asJsObject
          .fields("types")
          .asInstanceOf[JsArray]
          .elements :+ """{
                "inputFields":null,
                "name":"EventEnum",
                "description":null,
                "interfaces":null,
                "enumValues":[
                  {"name":"REMOVE","description":null,"isDeprecated":false,"deprecationReason":null},
                  {"name":"NEW","description":null,"isDeprecated":false,"deprecationReason":null},
                  {"name":"CHANGE","description":null,"isDeprecated":false,"deprecationReason":null}
                ],
                "fields":null,
                "kind":"ENUM",
                "possibleTypes":null
              }""".parseJson
        JsObject(
          "data" -> JsObject(
            "__schema" -> JsObject(
              res.asJsObject
                .fields("data")
                .asJsObject
                .fields("__schema")
                .asJsObject
                .fields + ("types" -> JsArray(typesWithEventEnum))
            )
          ),
          "errors" -> JsArray.empty
        )
      }
      .map(_.compactPrint.getBytes.iterator),
    10.seconds
  )

}
