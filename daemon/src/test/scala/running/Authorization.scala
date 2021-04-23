package running.authorizer

import pragma.jwtUtils._
import pragma.domain.SyntaxTree
import running._, running.storage._, running.operations._, running.utils._
import sangria.macros._
import spray.json._
import cats.implicits._
import org.scalatest._
import flatspec.AnyFlatSpec
import cats.effect.IO
import running.RunningImplicits._
import metacall.Caller
import scala.concurrent.ExecutionContext

class Authorization extends AnyFlatSpec with BeforeAndAfterAll {
  // TODO: Test `if` predicates here
  override protected def afterAll(): Unit = Caller.destroy()
  Caller.start(ExecutionContext.global)

  "Authorizer" should "authorize requests correctly" in {
    val code = """
    @1 @user
    model AU_User {
      @1 username: String @primary @publicCredential
      @2 password: String @secretCredential
      @3 isVerified: Boolean = false
    }

    allow CREATE AU_User
    deny SET_ON_CREATE AU_User.isVerified
    allow READ AU_User
    deny READ AU_User.password

    config { projectName = "test" }
    """

    val syntaxTree = SyntaxTree.from(code).get
    implicit val opParser = new OperationParser(syntaxTree)
    val testStorage = new TestStorage(syntaxTree)
    import testStorage._

    migrationEngine.migrate(Mode.Dev, code).unsafeRunSync()

    val authorizer = new Authorizer(
      syntaxTree,
      testStorage.storage,
      PFunctionExecutor.dummy[IO]
    )

    TestUtils.runGql {
      gql"""
       mutation createUser {
        AU_User {
          create(aU_User: {
            username: "John Doe",
            password: "password",
            isVerified: true
          }) {
            username
          }
        }
      }
      """
    }

    val req = Request.bareReqFrom {
      gql"""
      mutation createUser {
        AU_User {
          create(aU_User: {
            username: "John Doe",
            password: "password",
            isVerified: true
          }) {
            username
          }
        }
      }

      query getUser {
        AU_User {
          read(username: "John Doe") {
            username
            isVerified
            password
          }
        }
      }
      """
    }
    val reqOps = opParser
      .parse(req)
      .getOrElse(fail("Invalid query to fetch John Doe"))

    val result =
      authorizer(reqOps, req.user).unsafeRunSync().map(_.message)

    assert {
      result == Vector(
        "Denied setting field `isVerified` in `CREATE` operation",
        "Denied reading field `AU_User.password`"
      )
    }

  }

  "LOGIN, SET_ON_CREATE, and READ_ON_CREATE" should "be allowed by default" in {
    val code = """
    config { projectName = "skjbgskfhbdf" }
    allow CREATE AU_User3
    @user
    @1 model AU_User3 {
      @1 username: String @primary @publicCredential
      @2 password: String @secretCredential
    }
    """

    implicit val syntaxTree = SyntaxTree.from(code).get
    implicit val opParser = new OperationParser(syntaxTree)
    val testStorage = new TestStorage(syntaxTree)
    import testStorage._

    val authorizer = new Authorizer(
      syntaxTree,
      testStorage.storage,
      PFunctionExecutor.dummy[IO]
    )

    val req = Request.bareReqFrom {
      gql"""
      mutation {
        AU_User3 {
          create(aU_User3: { username: "jojo", password: "jojo123" }) {
            username
          }
        }
      }
      """
    }

    val opMap = opParser.parse(req).getOrElse(fail())
    val authzErrors = authorizer(opMap, None).unsafeRunSync()

    assert(authzErrors.isEmpty)
  }

  "Authorizer" should "take user roles into account" taggedAs (Tag("auth_2")) in {
    val code = """
    @1 model AU_Todo {
      @1 content: String
      @2 id: String @primary
    }

    @2 @user model AU_User2 {
      @1 username: String @primary @publicCredential
      @2 password: String @secretCredential
      @3 todos: [AU_Todo]
    }

    allow CREATE AU_User2
    allow READ AU_User2.todos

    role AU_User2 {
      allow READ self
      deny UPDATE self.username # Like Twitter
      allow [REMOVE_FROM, PUSH_TO] self.todos
    }

    config { projectName = "test" }
    """

    implicit val syntaxTree = SyntaxTree.from(code).get
    implicit val opParser = new OperationParser(syntaxTree)
    val testStorage = new TestStorage(syntaxTree)
    import testStorage._

    migrationEngine.migrate(Mode.Dev, code).unsafeRunSync()

    val reqWithoutRole = Request(
      hookData = None,
      body = None,
      user = None,
      query = gql"""
      mutation createUser {
        AU_User2 {
          create(aU_User2: {
            username: "John Doe",
            password: "password",
            todos: []
          })
        }
      }
      """,
      queryVariables = JsObject.empty,
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
            syntaxTree.modelsById("AU_User2"),
            johnDoe,
            Vector.empty
          )
      )
      .unsafeRunSync()

    val reqWithRole = Request(
      hookData = None,
      body = None,
      user = Some(JwtPayload(JsString("John Doe"), "AU_User2")),
      query = gql"""
      mutation updateUsername {
        AU_User2 {
          update(username: "John Doe", data: {username: "Jane Doe"}) {
            username
          }
        }
      }
      """,
      queryVariables = JsObject.empty,
      cookies = Map.empty,
      url = "",
      hostname = ""
    )

    val authorizer = new Authorizer(
      syntaxTree,
      testStorage.storage,
      PFunctionExecutor.dummy[IO]
    )

    val withoutRoleOps = opParser.parse(reqWithoutRole)
    val withRoleOps = opParser.parse(reqWithRole)

    val results = for {
      ops <- (withoutRoleOps, withRoleOps).bisequence
      (withoutRole, withRole) = ops
    } yield (
      authorizer(withoutRole, reqWithoutRole.user),
      authorizer(withRole, reqWithRole.user)
    ).bisequence.unsafeRunSync()

    results.foreach {
      case (result1, result2) => {
        assert(result1 === Vector.empty)
        assert(
          result2.map(_.message) == Vector(
            "Denied updating `username` field in `UPDATE` operation on `AU_User2`"
          )
        )
      }
    }
  }
}
