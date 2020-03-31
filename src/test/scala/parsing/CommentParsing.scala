package parsing

import org.scalatest._
import parsing._
import scala.util._

class CommentParsing extends FlatSpec {

  "Comments" should "be allowed anywhere in code" in {
    val code = """
    # Some comment
    @user #some other comment
    model User { # lalalalala
        # lilililili
        username: String @publicCredential # lulululu
    } # hahahahahahaha
    """

    new PragmaParser(code).syntaxTree.run() match {
      case Failure(_) => fail()
      case _          => ()
    }
  }

}
