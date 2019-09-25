package parsing
import scala.util._
import domain._
import primitives._

object Substitutor {
  def apply(st: SyntaxTree): Try[SyntaxTree] = {
    Success(SyntaxTree(Nil))
  }
}
