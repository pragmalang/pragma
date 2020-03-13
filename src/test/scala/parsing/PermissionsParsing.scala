import org.scalatest._
import domain._
import parsing._, HeavenlyParser._
import org.parboiled2.Position

class PermissionsParsing extends FlatSpec {
  "Permissions" should "be parsed correctly" in {
    val code = """
        allow ALL Book authorizors.f
        deny [CREATE, DELETE] Todo authorizors.g
      """
    val parsedPermissions = new HeavenlyParser(code).syntaxTree.run().get
    val expected = List(
      AccessRule(
        Allow,
        (Reference(List("Book"), None), None),
        List(Create, Read, Update, Delete),
        Some(
          Reference(
            List("authorizors", "f"),
            Some(PositionRange(Position(24, 2, 24), Position(37, 2, 37)))
          )
        ),
        Some(PositionRange(Position(9, 2, 9), Position(37, 2, 37)))
      ),
      AccessRule(
        Deny,
        (Reference(List("Todo"), None), None),
        List(Create, Delete),
        Some(
          Reference(
            List("authorizors", "g"),
            Some(PositionRange(Position(73, 3, 36), Position(86, 3, 49)))
          )
        ),
        Some(PositionRange(Position(37, 2, 37), Position(86, 3, 49)))
      )
    )
    assert(parsedPermissions == expected)
  }
}
