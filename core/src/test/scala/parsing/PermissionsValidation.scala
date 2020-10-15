package pragma.parsing

import util._
import pragma.domain._, utils._
import org.scalatest.flatspec.AnyFlatSpec

class PermissionsValidation extends AnyFlatSpec {
  "Substitutor" should "give correct errors for invalid permission use" in {
    val code = """
    @1 model Tweet {
      @1 content: String @primary
    }

    @2 @user model User {
      @1 handle: String @primary @publicCredential
      @2 password: String @secretCredential
      @3 tweets: [Tweet]
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

    config { projectName = "test" }
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
