package running

import org.scalatest._
import sangria.macros._
import pprint.pprintln

class RequestOperations extends FlatSpec {
  "Request `operations` attribute" should "be computed from user GraphQL query" in {
    val query = gql"""
        query user {
            User {
                read(id: "123") {
                    _id
                    username
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
    pprintln(query.operations)
  }
}
