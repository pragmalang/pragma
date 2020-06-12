package running

import org.scalatest._
import sangria.macros._
import running.pipeline.Operations
import domain._
import running.pipeline._
import spray.json._
import scala.collection.immutable._
import scala.util._

class RequestOperations extends FlatSpec {
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
                myself: read(_id: "123") @opLevel(arg1: 1) {
                    username @fieldLevel(arg2: 2)
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
    val ops = Operations.from(request)(syntaxTree)

    assert(ops.values.flatten.size == 2)

    assert(ops.flatMap(_._2.map(_.event)) == List(Read, Update))

    assert(ops(Some("user")).head.opArguments.head.name == "_id")

    assert(ops(Some("user"))(0).directives.head.arguments.head.name == "arg1")

    assert(
      ops(Some("user"))(0).innerReadOps.head.operation.directives.head.arguments.head.name == "arg2"
    )
  }
}
