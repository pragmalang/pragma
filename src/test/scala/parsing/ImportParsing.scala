import org.scalatest._
import domain._, primitives._
import parsing._
import scala.util._
import org.parboiled2.Position
import org.parboiled2.ParseError

class ImportParsing extends FlatSpec {
  "An import statement" should "be parsed correctly" in {
    val code = """
        import "./somefile.js" as f
        import "./someotherfile.js" as g
        """
    val parser = new HeavenlyParser(code)
    val syntaxTree = parser.syntaxTree.run()
    val expected = Success(
      List(
        HImport(
          "f",
          "./somefile.js",
          Some(PositionRange(Position(35, 2, 35), Position(36, 2, 36)))
        ),
        HImport(
          "g",
          "./someotherfile.js",
          Some(PositionRange(Position(76, 3, 40), Position(77, 3, 41)))
        )
      )
    )
    assert(syntaxTree == expected)
  }
}
