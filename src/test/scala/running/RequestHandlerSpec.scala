package running

import org.scalatest.flatspec.AnyFlatSpec
import domain.SyntaxTree
import running.TestUtils._
import running.storage.TestStorage
import sangria.macros._
import spray.json._
import cats.implicits._

class RequestHandlerSpec extends AnyFlatSpec {
  val code =
    """
    import "./src/test/scala/running/req-handler-test-hooks.js" as rhHooks

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
  migrationEngine.initialMigration match {
    case Right(mig) => mig.run(t).unsafeRunSync
    case Left(err) => {
      Console.err.println(
        "Failed to perform initial migration in `RequestHandlerSpec`"
      )
      throw err
    }
  }
  val reqHandler = new RequestHandler(syntaxTree, storage)

  "RequestHandler" should "execute write hooks correctly" in {
    val req = bareReqFrom {
      gql"""
      mutation createFathi {
        RH_User {
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

    val result = reqHandler.handle(req).unsafeRunSync match {
      case Right(value) => value
      case _ =>
        fail {
          "Req handler should return create op results successfully with hooks applied"
        }
    }

    val expected = JsObject(
      Map(
        "data" -> JsObject(
          Map(
            "createFathi" -> JsObject(
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

    val result = reqHandler.handle(req).unsafeRunSync match {
      case Right(value) => value
      case _ =>
        fail {
          "Request handler should not fail for RH_Todo read query"
        }
    }

    val expected = JsObject(
      Map(
        "data" -> JsObject(
          Map(
            "readGetPizza" -> JsObject(
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
