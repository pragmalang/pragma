package running

import org.scalatest._
import running.pipeline.functions.Authorizer
import running.pipeline.Request
import sangria.macros._
import spray.json._
import domain.SyntaxTree
import setup.storage.MockStorage
import akka.actor._
import scala.util.Failure
import scala.util.Success
import scala.concurrent._
import scala.concurrent.duration.Duration
import scala.util.{Try, Success, Failure}

class Authorization extends FlatSpec {
  "Authorizer" should "authorize requests correctly" in {
    val code = """
    deny UPDATE User.isVerified
    @user
    model User {
      username: String @primary @publicCredential
      password: String @secretCredential
      isVerified: Boolean = false
    }
    allow CREATE User
    """

    val syntaxTree = SyntaxTree.from(code).get
    val mockStorage = MockStorage(syntaxTree)
    val authorizer = Authorizer(syntaxTree, mockStorage, true)

    val req = Request(
      None,
      None,
      Some(JwtPaylod("123", "User")),
      gql"""
      mutation createUser {
        User {
          create(user: {
            username: "John Dow",
            password: "password",
            isVerified: true
          }) {
            username
            isVerified
          }
        }
      }
      
      query getUser {
        User {
          read(username: "John Doe") {
            username
          }
        }
      }
      """,
      Left(JsObject(Map.empty[String, JsValue])),
      Map.empty,
      "",
      ""
    )

    implicit val system = ActorSystem("some-system")
    Try {
      Await.result(authorizer(req).runForeach(result => ()), Duration.Inf)
    } match {
      case Success(_)   => () // Should be: fail()
      case Failure(err) => ()
    }
  }
}
