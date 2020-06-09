package parsing

import org.scalatest._
import parsing._
import scala.util._

class CommentParsing extends FlatSpec {

  "Comments" should "be allowed anywhere in code" in {
    val code = """
    # Some comment
    @1 @user #some other comment
    model User { # lalalalala
        # lilililili
        @1 username: String @publicCredential # lulululu
    } # hahahahahahaha
    """

    new PragmaParser(code).syntaxTree.run() match {
      case Failure(_) => fail()
      case _          => ()
    }
  }

}
