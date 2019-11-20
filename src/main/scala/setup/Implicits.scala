package setup
import sangria.parser.QueryParser
import scala.language.implicitConversions
import scala.language.postfixOps
import sys.process._
import scala.util.{Success, Failure}

package object Implicits {
  implicit def parseQuery(query: String) = QueryParser.parse(query)
}
