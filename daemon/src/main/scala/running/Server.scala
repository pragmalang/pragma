package running

import running.utils.QueryError
import cats.effect._
import org.http4s._, org.http4s.dsl.io._
import org.http4s.util._, org.http4s.server.middleware._
import scala.concurrent.ExecutionContext.global
import fs2._
import cats.implicits._
import sangria.parser.QueryParser
import spray.json._
import setup.schemaGenerator.ApiSchemaGenerator
import running.RequestHandler
import storage.postgres._
import pragma.domain._, pragma.domain.utils._
import sangria.marshalling.sprayJson._
import sangria.schema.Schema, sangria.execution.Executor

class Server(
    jwtCodec: JwtCodec,
    storage: Postgres[IO],
    currentSyntaxTree: SyntaxTree,
    funcExecutor: PFunctionExecutor[IO]
)(implicit cs: ContextShift[IO]) {
  import Server._

  def gqlSchema =
    Schema.buildFromAst(
      ApiSchemaGenerator(currentSyntaxTree).build
    )

  val reqHandler =
    new RequestHandler[Postgres[IO], IO](
      currentSyntaxTree,
      storage,
      funcExecutor
    )

  val routes =
    HttpRoutes.of[IO] {
      case req @ POST -> Root => {
        val res: Stream[IO, Response[IO]] = req.bodyText map { body =>
          val jsonBody = body.parseJson.asJsObject
          if (jsonBody.fields("operationName") ==
                JsString("IntrospectionQuery"))
            Response[IO](
              Status(200),
              HttpVersion.`HTTP/2.0`,
              Headers(List(Header("Content-Type", "application/json"))),
              body = Stream
                .eval(introspectionResult(jsonBody))
                .flatMap(it => Stream.fromIterator[IO](it))
            )
          else {
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
              .handle(preq)
              .recover {
                case e: Throwable => jsonFrom(e)
              }

            Response[IO](
              Status(200),
              HttpVersion.`HTTP/2.0`,
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
          HttpVersion.`HTTP/2.0`,
          Headers(List(Header("Content-Type", "text/html"))),
          body = Stream.emits(assets.playgroundHtml.getBytes)
        ).pure[IO]
    }

  val handle = CORS(GZip(routes))

  def introspectionResult(query: JsObject): IO[Iterator[Byte]] = IO.fromFuture {
    implicit val ctx = global
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
