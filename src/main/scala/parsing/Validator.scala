package parsing
import domain._
import scala.util._

object Validator {
  def apply(constructs: List[HConstruct]): Try[List[HConstruct]] = {
    Success(Nil)
  }

  def checkFieldValueType(constructs: List[HConstruct]) = Nil
}
