import domain._
import parsing._, PragmaParser._
import org.parboiled2.Position
import org.scalatest.flatspec.AnyFlatSpec

class PermissionsParsing extends AnyFlatSpec {
  "Permissions" should "be parsed correctly" in {
    val code = """
        allow ALL Book if authorizors.f
        deny [CREATE, DELETE] Todo if authorizors.g
      """
    val parsedPermissions = new PragmaParser(code).syntaxTree.run().get
    val expected = List(
      AccessRule(
        Allow,
        (Reference(List("Book"), None), None),
        Set(All),
        Some(
          Reference(
            List("authorizors", "f"),
            Some(PositionRange(Position(27, 2, 27), Position(40, 2, 40)))
          )
        ),
        Some(PositionRange(Position(9, 2, 9), Position(40, 2, 40)))
      ),
      AccessRule(
        Deny,
        (Reference(List("Todo"), None), None),
        Set(Create, Delete),
        Some(
          Reference(
            List("authorizors", "g"),
            Some(PositionRange(Position(79, 3, 39), Position(92, 3, 52)))
          )
        ),
        Some(PositionRange(Position(49, 3, 9), Position(92, 3, 52)))
      )
    )
    assert(parsedPermissions == expected)
  }
}
