package parsing

import org.scalatest._
import domain.SyntaxTree
import util._
import domain._, utils._

class PermissionsValidation extends FlatSpec {
  "Substitutor" should "give correct errors for invalid permission use" in {
    val code = """
    @1 model Tweet {
      content: String
    }

    @2 @user model User {
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
    val expectedErrors = List(
      "Permission `PUSH_TO` cannot be specified for primitive field `handle`",
      "Permission `MUTATE` cannot be specified for primitive field `password`"
    )

    syntaxTree match {
      case Failure(err: UserError) =>
        assert(err.errors.map(_._1) == expectedErrors)
      case _ => fail("Validation should fail with expected errors")
    }
  }
}
