package running

import org.scalatest.flatspec.AnyFlatSpec
import pragma.domain.SyntaxTree
import running.TestUtils._
import running.storage.TestStorage
import sangria.macros._
import spray.json._
import cats.implicits._
import cats.effect.IO
import org.http4s.Uri

class RequestHandlerSpec extends AnyFlatSpec {
  val code =
    """
    import "./daemon/src/test/scala/running/req-handler-test-hooks.js" as rhHooks

    @onWrite(function: rhHooks.prependMrToUsername)
    @onWrite(function: rhHooks.setPriorityTodo)
    @1 model RH_User {
        @1 username: String @primary
        @2 todos: [RH_Todo]
        @3 priorityTodo: RH_Todo?
    }

    @onRead(function: rhHooks.emphasizeUndone)
    @2 model RH_Todo {
        @1 title: String @primary
        @2 content: String
        @3 done: Boolean
    }

    allow ALL RH_User
    allow ALL RH_Todo
    """

  val syntaxTree = SyntaxTree.from(code).get
  val testStorage = new TestStorage(syntaxTree)
  import testStorage._

  migrationEngine.initialMigration.unsafeRunSync.run(t).unsafeRunSync()

  val reqHandler = new RequestHandler(
    syntaxTree,
    storage,
    new PFunctionExecutor[IO](
      WskConfig(
        1,
        1,
        Uri
          .fromString("http://localhost/")
          .getOrElse(fail("Invalid WSK host URI"))
      )
    )
  )

  "RequestHandler" should "execute write hooks correctly" in {
    val req = bareReqFrom {
      gql"""
      mutation createFathi {
        user: RH_User {
          create(rH_User: {
            username: "Fathi",
            todos: [
              {title: "Get pizza", content: "We need to eat", done: false},
              {title: "Dishes", content: "Wash em'", done: true}
            ],
            priorityTodo: null
          }) {
            username
            priorityTodo {
              title
            }
          }
        }
      }
      """
    }

    val result = reqHandler.handle(req).unsafeRunSync

    val expected = JsObject(
      Map(
        "data" -> JsObject(
          Map(
            "user" -> JsObject(
              Map(
                "create" -> JsObject(
                  Map(
                    "username" -> JsString("Mr. Fathi"), // Because of `rhHooks.prependMrToUsername`
                    "priorityTodo" -> JsObject( // Because of `rhHooks.setPriorityTodo`
                      Map("title" -> JsString("** Get pizza **"))
                    )
                  )
                )
              )
            )
          )
        )
      )
    )

    assert(result === expected)
  }

  "Request handler" should "apply read hooks correctly" in {
    val req = bareReqFrom {
      gql"""
      query readGetPizza {
        RH_Todo {
          read(title: "Get pizza") {
            title
            content
          }
        }
      }
      """
    }

    val result = reqHandler.handle(req).unsafeRunSync

    val expected = JsObject(
      Map(
        "data" -> JsObject(
          Map(
            "RH_Todo" -> JsObject(
              Map(
                "read" -> JsObject(
                  Map(
                    "title" -> JsString("** Get pizza **"), // Because of `emphasizeUndone`
                    "content" -> JsString("We need to eat")
                  )
                )
              )
            )
          )
        )
      )
    )

    assert(result === expected)
  }

}
