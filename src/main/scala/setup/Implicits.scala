package setup
import sangria.parser.QueryParser
import scala.language.implicitConversions

object Implicits {
    implicit def parseQuery(query: String) = QueryParser.parse(query).get
}