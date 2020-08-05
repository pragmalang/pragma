package parsing

import parsing._
import scala.util._
import org.scalatest.flatspec.AnyFlatSpec

class CommentParsing extends AnyFlatSpec {

  "Comments" should "be allowed anywhere in code" in {
    val code = """
    # Some comment
    @1 @user #some other comment
    model User { # lalalalala
        # lilililili
        @1 username: String @publicCredential @primary # lulululu
    } # hahahahahahaha
    """

    new PragmaParser(code).syntaxTree.run() match {
      case Failure(_) => fail()
      case _          => ()
    }
  }

}
