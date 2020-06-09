package running

import org.scalatest._
import running.pipeline.functions.Authorizer
import running.pipeline.Request
import sangria.macros._
import spray.json._
import domain.SyntaxTree
import setup.storage.MockStorage
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.util._
import domain.utils.AuthorizationError
import cats.implicits._

class Authorization extends FlatSpec {
  "Authorizer" should "authorize requests correctly" in {
    val code = """
    @1 @user
    model User {
      @1 username: String @primary @publicCredential
      @2 password: String @secretCredential
      @3 isVerified: Boolean = false
    }

    deny SET_ON_CREATE User.isVerified
    allow CREATE User

    role User {
      allow READ self
    }
    """

    val syntaxTree = SyntaxTree.from(code).get
    val mockStorage = MockStorage.storage
    val authorizer = new Authorizer(syntaxTree, mockStorage, devModeOn = true)

    val req = Request(
      None,
      None,
      Some(JwtPaylod("John Doe", "User")),
      gql"""
      mutation createUser {
        User {
          create(user: {
            username: "John Dow",
            password: "password",
            isVerified: true
          })
        }
      }
      
      query getUser { # should succeed because user is logged in
        User {
          read(username: "John Doe") {
            username
            isVerified
          }
        }
      }
      """,
      Left(JsObject(Map.empty[String, JsValue])),
      Map.empty,
      "",
      ""
    )

    val result = Await.result(authorizer(req), Duration.Inf)
    assert(
      result == Left(
        Vector(
          AuthorizationError(
            "Denied setting attribute in `CREATE` operation"
          )
        )
      ),
      "Request should be denied because SET_ON_CREATE is denied for `isVerified`"
    )
  }

  "Authorizer" should "handle user predicates correctly" in {
    val code = """
    @1 model Todo {
      @1 content: String
    }

    @2 @user model User {
      @1 username: String @primary @publicCredential
      @2 password: String @secretCredential
      @3 todos: [Todo]
    }

    allow CREATE User
    allow READ User.todos

    role User {
      allow READ self
      deny UPDATE self.username # Like Twitter
      allow [REMOVE_FROM, PUSH_TO] self.todos
    }
    """

    val syntaxTree = SyntaxTree.from(code).get
    val reqWithoutRole = Request(
      hookData = None,
      body = None,
      user = None,
      query = gql"""
      mutation createUser {
        User {
          create(user: {
            username: "John Doe",
            password: "password",
            todos: []
          })
        }
      }
      """,
      queryVariables = Left(JsObject.empty),
      cookies = Map.empty,
      url = "",
      hostname = ""
    )
    val reqWithRole = Request(
      hookData = None,
      body = None,
      user = Some(JwtPaylod("John Doe", "User")),
      query = gql"""
      mutation updateUsername {
        User {
          update(username: "John Doe", user: {username: "Jane Doe"}) {
            username
          }
        }
      }
      """,
      queryVariables = Left(JsObject.empty),
      cookies = Map.empty,
      url = "",
      hostname = ""
    )

    val mockStorage = MockStorage.storage
    val authorizer = new Authorizer(syntaxTree, mockStorage, devModeOn = true)
    val results = Await.result(
      Future.sequence(
        authorizer(reqWithoutRole) ::
          authorizer(reqWithRole) :: Nil
      ),
      Duration.Inf
    )
    pprint.pprintln(results)
  }
}
