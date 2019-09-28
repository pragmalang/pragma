import org.scalatest._
import domain._, primitives._
import parsing._
import scala.util._
import org.parboiled2.Position
import org.parboiled2.ParseError

class ImportParsing extends FlatSpec {
  "An import statement" should "be parsed correctly" in {
    val code = """
        import f from "./somefile.js"
        import g from "./someotherfile.js" as G
        """
    val parser = new HeavenlyParser(code)
    val syntaxTree = parser.syntaxTree.run()
    val expected = Success(
      List(
        HImport(
          "f",
          "./somefile.js",
          None,
          Some(PositionRange(Position(16, 2, 16), Position(17, 2, 17)))
        ),
        HImport(
          "g",
          "./someotherfile.js",
          Some("G"),
          Some(PositionRange(Position(54, 3, 16), Position(55, 3, 17)))
        )
      )
    )
    assert(syntaxTree == expected)
  }
}
