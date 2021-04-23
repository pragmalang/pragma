package running

import org.scalatest.flatspec.AnyFlatSpec
import pragma.domain._
import running.storage.TestStorage, running.utils._
import sangria.macros._
import spray.json._
import cats.implicits._, cats.effect.IO
import scala.concurrent.ExecutionContext
import RunningImplicits._
import metacall._
import pragma.tests.utils._
import org.scalatest.BeforeAndAfterAll

class RequestHandlerSpec extends AnyFlatSpec with BeforeAndAfterAll {

  override protected def afterAll(): Unit = {
    println("AFTERALL STARTED")
    MetaCallManager.finish()
    println("AFTERALL FINISHED")
  }

  val code =
    """
  import "./daemon/src/test/scala/running/req-handler-test-hooks.js" as rhHooks { runtime = "nodejs:10" }
  
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
  
  config { projectName = "RH" }
  """

  val syntaxTree = SyntaxTree.from(code).get
  val testStorage = new TestStorage(syntaxTree)
  import testStorage._

  migrationEngine.migrate(Mode.Dev, code).unsafeRunSync()

  implicit val cs = IO.contextShift(ExecutionContext.global)

  private val imp = syntaxTree.imports.head

  MetaCallManager.start()
  Caller.loadFile(Runtime.Node, imp.filePath, "rhHooks")

  val reqHandler = new RequestHandler(
    syntaxTree,
    storage,
    new PFunctionExecutor[IO]
  )

  "RequestHandler" should "execute write hooks correctly" in {
    val req = running.Request.bareReqFrom {
      gql"""
      mutation createFathi {
        user: RH_User {
          create(rH_User: {
     
           username: "Fathi",
            todos: [
              { title: "Get pizza", content: "We need to eat", done: false },
              { title: "Dishes", content: "Wash em'", done: true }
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

    val result = reqHandler.handle(req).unsafeRunSync()

    val expected = JsObject(
      Map(
        "data" -> JsObject(
          Map(
            "user" -> JsObject(
              Map(
                "create" -> JsObject(
                  Map(
                    "username" -> JsString(
                      "Mr. Fathi"
                    ), // Because of `rhHooks.prependMrToUsername`
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
    val req = running.Request.bareReqFrom {
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

    val result = reqHandler.handle(req).unsafeRunSync()

    val expected = JsObject(
      Map(
        "data" -> JsObject(
          Map(
            "RH_Todo" -> JsObject(
              Map(
                "read" -> JsObject(
                  Map(
                    "title" -> JsString(
                      "** Get pizza **"
                    ), // Because of `emphasizeUndone`
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
