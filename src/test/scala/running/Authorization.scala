package running

import domain.SyntaxTree
import domain.utils.AuthorizationError
import running._, running.storage._
import sangria.macros._
import spray.json._
import scala.util._
import cats.implicits._
import doobie.implicits._
import org.scalatest.flatspec.AnyFlatSpec

class Authorization extends AnyFlatSpec {
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
    val testStorage = new TestStorage(syntaxTree)
    import testStorage._
    migrationEngine.initialMigration.get.run(t).transact(t).unsafeRunSync()
    val authorizer =
      new Authorizer(syntaxTree, testStorage.storage, devModeOn = true)

    val johnDoe = JsObject(
      Map(
        "username" -> JsString("John Doe"),
        "password" -> JsString("123"),
        "isVerified" -> JsFalse
      )
    )
    queryEngine
      .runQuery(
        queryEngine
          .createOneRecord(syntaxTree.modelsById("User"), johnDoe, Vector.empty)
      )
      .unsafeRunSync()

    val req = Request(
      None,
      None,
      Some(JwtPayload(JsString("John Doe"), "User")),
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
    val reqOps = Operations.from(req)(syntaxTree)

    val result = reqOps.flatMap { ops =>
      authorizer(ops, req.user).unsafeRunSync
    }
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
      @2 id: String @primary
    }

    @2 @user model User2 {
      @1 username: String @primary @publicCredential
      @2 password: String @secretCredential
      @3 todos: [Todo]
    }

    allow CREATE User2
    allow READ User2.todos

    role User2 {
      allow READ self
      deny UPDATE self.username # Like Twitter
      allow [REMOVE_FROM, PUSH_TO] self.todos
    }
    """

    implicit val syntaxTree = SyntaxTree.from(code).get
    val testStorage = new TestStorage(syntaxTree)
    import testStorage._
    migrationEngine.initialMigration.get.run(t).transact(t).unsafeRunSync()

    val reqWithoutRole = Request(
      hookData = None,
      body = None,
      user = None,
      query = gql"""
      mutation createUser {
        User2 {
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
    val johnDoe = JsObject(
      Map(
        "username" -> JsString("John Doe"),
        "password" -> JsString("123"),
        "todos" -> JsArray.empty
      )
    )
    queryEngine
      .runQuery(
        queryEngine
          .createOneRecord(
            syntaxTree.modelsById("User2"),
            johnDoe,
            Vector.empty
          )
      )
      .unsafeRunSync()
    val reqWithRole = Request(
      hookData = None,
      body = None,
      user = Some(JwtPayload(JsString("John Doe"), "User")),
      query = gql"""
      mutation updateUsername {
        User2 {
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

    val authorizer =
      new Authorizer(syntaxTree, testStorage.storage, devModeOn = true)

    val withoutRoleOps = Operations.from(reqWithoutRole)
    val withRoleOps = Operations.from(reqWithRole)

    val results = for {
      ops <- (withoutRoleOps, withRoleOps).bisequence
      (withoutRole, withRole) = ops
    } yield
      (
        authorizer(withoutRole, reqWithoutRole.user) ::
          authorizer(withRole, reqWithRole.user) :: Nil
      ).sequence

    pprint.pprintln(results)
  }
}
