package setup
import sangria.parser.QueryParser
import scala.language.implicitConversions
import scala.language.postfixOps
import sys.process._
import scala.util.{Success, Failure}

package object Implicits {
  implicit def parseQuery(query: String) = QueryParser.parse(query)
  implicit class Command(s: String) {
    def $(msg: String) = s ! match {
      case 1 => Failure(new Exception(msg))
      case 0 => Success(())
    }
  }
}
