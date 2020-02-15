import org.scalatest._
import domain._
import parsing._
import scala.util._
import org.parboiled2.Position

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
          Some(PositionRange(Position(14, 2, 14), Position(21, 2, 21)))
        )
      )
    )
    assert(parsedEnum == expected)
  }
}
