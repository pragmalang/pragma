import org.scalatest._
import domain._, primitives._
import parsing._
import scala.util._

class EnumParsing extends FlatSpec {
  "An enum" should "be parsed correctly" in {
    val code = """
        enum WeekDay {
            "Sunday"
            "Monday", Tuesday
            Wednesday,
            Thursday
            Friday,
            Saturday
        }
        """
    val parsedEnum = new HeavenlyParser(code).syntaxTree.run()
    val expected = Success(
      SyntaxTree(
        List(
          HEnum(
            "WeekDay",
            List(
              "Sunday",
              "Monday",
              "Tuesday",
              "Wednesday",
              "Thursday",
              "Friday",
              "Saturday"
            ),
            None
          )
        )
      )
    )
    assert(parsedEnum == expected)
  }
}
