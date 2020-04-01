package running

import org.scalatest._
import sangria.macros._
import running.pipeline.Operation
import domain._
import running.pipeline._
import spray.json._
import scala.collection.immutable._
import scala.util._

class RequestOperations extends FlatSpec {
  "Request `operations` attribute" should "be computed from user GraphQL query" in {
    val code = """
    import "./src/test/scala/parsing/test-functions.js" as fns

    @user model User {
        username: String @publicCredential
        todos: Todo
        friend: User?
    }

    role User {
        allow READ User if fns.isSelf
    }

    model Todo {
        title: String
        content: String
    }
    """
    val syntaxTree = SyntaxTree.from(code).get

    val query = gql"""
        query user {
            User @modelLevelDir(arg1: 1) {
                myself: read(_id: "123") @opLevel(arg2: 2) {
                    username @fieldLevel(arg3: 3)
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
                update(id: "22234", data: {
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
    val ops = Operation.operationsFrom(request)(syntaxTree)
    assert(ops.values.flatten.size == 4)
    assert(ops(Some("user")).head.opArguments.head.name == "_id")
    assert(
      ops(Some("user"))(2).modelLevelDirectives.head.arguments.head.name == "arg1"
    )
    assert(ops(Some("user"))(1).directives.head.arguments.head.name == "arg2")
    assert(
      ops(Some("user"))(0).fieldPath.head.directives.head.arguments.head.name == "arg3"
    )
  }
}
