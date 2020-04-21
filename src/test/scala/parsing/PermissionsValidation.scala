package parsing

import org.scalatest._
import domain.SyntaxTree

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

    allow CREATE user

    role User {
      allow ALL self
      deny UPDATE self.handle
      allow [REMOVE_FROM, PUSH_TO] self.tweets
    }
    """
    val st = SyntaxTree.from(code)
    pprint.pprintln(st)
    // val syntaxTree = SyntaxTree.from(code)
    // pprint.pprintln(syntaxTree)
  }
}
