package running

import sangria.macros._
import pragma.domain._
import running._, running.operations._
import spray.json._
import scala.collection.immutable._
import scala.util._
import org.scalatest.flatspec.AnyFlatSpec
import cats.implicits._

class RequestOperations extends AnyFlatSpec {
  "Request operations" should "be computed from user GraphQL query" in {
    val code = """
    import "./core/src/test/scala/parsing/test-functions.js" as fns

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
    val opParser = new OperationParser(syntaxTree)

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
                update(title: "22234", todo: {
                    content: "Clean the dishes"
                }) {
                    content
                }
            }
        }

        mutation loginAnas {
          User {
            loginByUsername(username: "Anas") {
              username
              friend {
                username
              }
            }
          }
        }
        """
    val request = Request(
      None,
      None,
      None,
      query,
      JsObject(Map.empty[String, JsValue]),
      Map.empty,
      "http://localhost:8080/gql",
      "localhost"
    )
    val ops = opParser.parse(request)(syntaxTree)

    ops match {
      case Left(err) =>
        fail {
          s"Operations should be constructed successfully, instead they failed with $err"
        }
      case Right(ops) => {
        // Number of operation groups
        assert(ops.values.flatten.size == 3)

        assert(
          ops.flatMap(_._2.values.flatten.map(_.event)) ===
            List(Read, Update, Login)
        )

        assert(
          ops(Some("user"))("User").head.opArguments == ReadArgs(
            JsString("123")
          )
        )

        assert(ops(Some("user"))("User").head.innerReadOps.length == 2)

        assert(
          ops(Some("updateTodo"))("Todo").head.opArguments == UpdateArgs(
            ObjectWithId(
              JsObject(Map("content" -> JsString("Clean the dishes"))),
              JsString("22234")
            )
          )
        )

        val anasLoginArgs = ops(Some("loginAnas"))("User").head.opArguments
          .asInstanceOf[LoginArgs]

        assert(anasLoginArgs.publicCredentialField.id == "username")
        assert(anasLoginArgs.secretCredentialValue == None)
        assert(anasLoginArgs.publicCredentialValue == JsString("Anas"))

      }
    }

  }
}
