package running

import running.utils.QueryError
import cats.effect._
import org.http4s._, org.http4s.dsl.io._
import org.http4s.server.blaze._, org.http4s.server.Router
import org.http4s.util._, org.http4s.implicits._
import scala.concurrent.ExecutionContext.Implicits.global
import fs2._
import cats.implicits._
import sangria.parser.QueryParser
import spray.json._
import setup.schemaGenerator.ApiSchemaGenerator
import running.RequestHandler
import storage.postgres._
import assets.asciiLogo
import domain._
import domain.utils._
import sangria.marshalling.sprayJson._
import sangria.schema.Schema, sangria.execution.Executor

class Server(
    jwtCodec: JwtCodec,
    storage: Resource[IO, Postgres[IO]],
    currentSyntaxTree: SyntaxTree
) extends IOApp {
  import Server._

  val gqlSchema =
    Schema.buildFromAst(
      ApiSchemaGenerator(currentSyntaxTree).buildApiSchemaAsDocument
    )

  val reqHandler =
    storage.map { s =>
      new RequestHandler[Postgres[IO], IO](
        currentSyntaxTree,
        s
      )
    }

  val routes = HttpRoutes.of[IO] {
    case req @ POST -> Root => {
      val res: Stream[IO, Response[IO]] = req.bodyAsText map { body =>
        val jsonBody = body.parseJson.asJsObject
        if (jsonBody.fields("operationName") == JsString("IntrospectionQuery")) {
          Response[IO](
            Status(200),
            HttpVersion(1, 1),
            Headers(List(Header("Content-Type", "application/json"))),
            body = Stream
              .eval(introspectionResult(jsonBody))
              .flatMap(it => Stream.fromIterator[IO](it))
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
              case o: JsObject => o
              case _           => throw UserError("Invalid GraphQL query variables")
            },
            user = req.headers
              .get(CaseInsensitiveString("Authorization"))
              .flatMap(h => jwtCodec.decode(h.value).toOption)
          )
          val resJson = reqHandler
            .use(rh => rh.handle(preq))
            .map[JsValue] {
              case Left(e)    => jsonFrom(e)
              case Right(obj) => obj
            }
            .recover {
              case e: Throwable => jsonFrom(e)
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
    val msg =
      s"""
        ${asciiLogo.split("\n").map(line => (" " * 24) + line).mkString("\n")}

        Pragma GraphQL server is now running on port 3030.

                  ${Console.GREEN}${Console.BOLD}http://localhost:3030/graphql${Console.RESET}
      """

    val printMsg = IO(println(msg))
    printMsg *> BlazeServerBuilder[IO](global)
      .bindHttp(3030, "localhost")
      .withHttpApp(Router("/graphql" -> routes).orNotFound)
      .serve
      .compile
      .drain
      .as(ExitCode.Success)
  }

  def introspectionResult(query: JsObject): IO[Iterator[Byte]] = IO.fromFuture {
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
          )
        )
      }
      .map(_.compactPrint.getBytes.iterator)
      .pure[IO]
  }

}
object Server {

  private def jsonFrom(err: Throwable): JsObject = err match {
    case UserError(errors) =>
      JsObject {
        "errors" -> JsArray {
          errors
            .map(err => JsObject("message" -> JsString(err._1)))
            .toVector
        }
      }

    case QueryError(messages) =>
      JsObject {
        "errors" -> JsArray {
          messages.map(msg => JsObject("message" -> JsString(msg)))
        }
      }
    case err =>
      JsObject {
        "errors" -> JsArray {
          JsObject("message" -> JsString(err.getMessage))
        }
      }
  }

}
