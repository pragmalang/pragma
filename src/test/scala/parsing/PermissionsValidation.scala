package parsing

import org.scalatest._
import domain.SyntaxTree
import util._
import domain._, utils._
import org.parboiled2._

class PermissionsValidation extends FlatSpec {
  "Substitutor" should "give correct errors for invalid permission use" in {
    val code = """
    model Tweet {
      content: String
    }

    @user model User {
      handle: String @primary @publicCredential
      password: String @secretCredential
      tweets: [Tweet]
    }

    allow CREATE User

    role User {
      allow ALL self
      deny UPDATE self.handle
      allow PUSH_TO self.handle
      allow REMOVE_FROM self.tweets
      deny MUTATE self.password
      deny MUTATE self.tweets
    }
    """
    val syntaxTree = SyntaxTree.from(code)
    val expectedErrors = Failure(
      UserError(
        List(
          (
            "Permission `PUSH_TO` cannot be specified for primitive field `handle`",
            Some(PositionRange(Position(285, 17, 7), Position(310, 17, 32)))
          ),
          (
            "Permission `MUTATE` cannot be specified for primitive field `password`",
            Some(PositionRange(Position(353, 19, 7), Position(378, 19, 32)))
          )
        )
      )
    )

    assert(expectedErrors == syntaxTree)
  }
}
