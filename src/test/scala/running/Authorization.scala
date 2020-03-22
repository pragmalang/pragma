package running

import org.scalatest._
import running.pipeline.functions.Authorizer
import running.pipeline.Request
import sangria.macros._
import spray.json._
import domain.SyntaxTree
import setup.storage.MockStorage
import akka.actor._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits._
import scala.util.Failure
import scala.util.Success

class Authorization extends FlatSpec {
  "Authorizer" should "authorize requests correctly" in {
    val code = """
    @user
    model User {
      username: String @primary @publicCredential
      password: String @secretCredential
      isVerified: Boolean = false
    }
    allow CREATE User
    deny [SET_ON_CREATE] User.isVerified
    """

    val syntaxTree = SyntaxTree.from(code) match {
      case Failure(exception) => {
        pprint.pprintln(exception)
        throw exception
      }
      case Success(value) => value
    }
    val mockStorage = MockStorage(syntaxTree)
    val authorizer = Authorizer(syntaxTree, mockStorage, true)

    // pprint.pprintln(syntaxTree)

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
      
   #   query getUser {
   #     User {
   #       read(username: "John Doe") {
   #         username
   #       }
   #     }
   #   }
      """,
      Left(JsObject(Map.empty[String, JsValue])),
      Map.empty,
      "",
      ""
    )

    implicit val system = ActorSystem("test-system")
    authorizer(req).runForeach { result =>
      println(result)
    } recoverWith {
      case e => {
        // pprint.pprintln(e)
        Future(req)
      }
    }
  }
}
