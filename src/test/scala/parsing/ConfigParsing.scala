import org.scalatest._
import domain._
import parsing._
import scala.util._
import org.parboiled2.Position

class ConfigParsing extends FlatSpec {
  "Config block" should "be parsed correctly" in {
    val code = """
        config {
            someKey = "some value"
            someOtherKey = 42
        }
        """
    val syntaxTree = new PragmaParser(code).syntaxTree.run()
    val expected = Success(
      List(
        PConfig(
          List(
            ConfigEntry(
              "someKey",
              PStringValue("some value"),
              Some(PositionRange(Position(30, 3, 13), Position(37, 3, 20)))
            ),
            ConfigEntry(
              "someOtherKey",
              PIntValue(42),
              Some(PositionRange(Position(65, 4, 13), Position(77, 4, 25)))
            )
          ),
          Some(PositionRange(Position(9, 2, 9), Position(16, 2, 16)))
        )
      )
    )
    assert(expected == syntaxTree)
  }
}
