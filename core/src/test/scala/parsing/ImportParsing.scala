package pragma.parsing

import pragma.domain._
import pragma.parsing._
import scala.util._
import org.parboiled2.Position
import org.scalatest.flatspec.AnyFlatSpec

class ImportParsing extends AnyFlatSpec {
  "An import statement" should "be parsed correctly" in {
    val code = """
        import "./somefile.js" as f
        import "./someotherfile.js" as g { confProp = 42 }
        """
    val parser = new PragmaParser(code)
    val syntaxTree = parser.syntaxTree.run()
    pprint.pprintln(syntaxTree.get(1))
    val expected = Success(
      List(
        PImport(
          "f",
          "./somefile.js",
          None,
          Some(PositionRange(Position(35, 2, 35), Position(36, 2, 36)))
        ),
        PImport(
          "g",
          "./someotherfile.js",
          Some(
            PConfig(
              List(
                ConfigEntry(
                  "confProp",
                  PIntValue(42L),
                  Some(PositionRange(Position(80, 3, 44), Position(88, 3, 52)))
                )
              ),
              Some(PositionRange(Position(78, 3, 42), Position(95, 3, 59)))
            )
          ),
          Some(PositionRange(Position(76, 3, 40), Position(77, 3, 41)))
        )
      )
    )
    assert(syntaxTree == expected)
  }
}
