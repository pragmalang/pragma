package running

import sangria.macros._
import domain._
import running._
import spray.json._
import scala.collection.immutable._
import scala.util._
import org.scalatest.flatspec.AnyFlatSpec

class RequestOperations extends AnyFlatSpec {
  "Request operations" should "be computed from user GraphQL query" in {
    val code = """
    import "./src/test/scala/parsing/test-functions.js" as fns

    @user @1 model User {
        @1 username: String @publicCredential @primary
        @2 todos: Todo
        @3 friend: User?
    }

    role User {
        allow READ User if fns.isSelf
    }

    @2 model Todo {
        @1 title: String @primary
        @2 content: String
    }
    """
    val syntaxTree = SyntaxTree.from(code).get

    val query = gql"""
        query user {
            User {
                myself: read(username: "123") @opLevel(arg1: 1) {
                    username
                    friend {
                        friend {
                            username
                            todos
                        }
                    }
                }
            }
        }
        mutation updateTodo {
            Todo {
                update(title: "22234", data: {
                    content: "Clean the dishes"
                }) {
                    content
                }
            }
        }
        """
    val request = Request(
      None,
      None,
      None,
      query,
      Left(JsObject(Map.empty[String, JsValue])),
      Map.empty,
      "http://localhost:8080/gql",
      "localhost"
    )
    val ops = Operations.from(request)(syntaxTree)

    ops match {
      case Left(err) =>
        fail(
          s"Operations should be constructed successfully, instead they failed with $err"
        )
      case Right(ops) => {
        assert(ops.values.flatten.size == 2)

        assert(ops.flatMap(_._2.map(_.event)) == List(Read, Update))

        assert(ops(Some("user")).head.opArguments == ReadArgs(JsString("123")))

        assert(ops(Some("user")).head.innerReadOps.length == 2)

        assert(
          ops(Some("updateTodo")).head.opArguments == UpdateArgs(
            ObjectWithId(
              JsObject(Map("content" -> JsString("Clean the dishes"))),
              JsString("22234")
            )
          )
        )
      }
    }

  }
}
