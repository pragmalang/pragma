import org.scalatest._
import domain._, primitives._
import parsing._, HeavenlyParser._
import scala.util._
import org.parboiled2.Position
import scala.collection.immutable.ListMap

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
        ShapeResource(
          ResourceReference(
            "Book",
            None,
            Some(PositionRange(Position(19, 2, 19), Position(23, 2, 23)))
          )
        ),
        List(Create, Read, Update, Delete),
        Reference(
          "authorizors",
          Some(
            Reference(
              "f",
              None,
              Some(PositionRange(Position(36, 2, 36), Position(37, 2, 37)))
            )
          ),
          Some(PositionRange(Position(24, 2, 24), Position(37, 2, 37)))
        ),
        Some(PositionRange(Position(9, 2, 9), Position(37, 2, 37)))
      ),
      AccessRule(
        Deny,
        ShapeResource(
          ResourceReference(
            "Todo",
            None,
            Some(PositionRange(Position(68, 3, 31), Position(72, 3, 35)))
          )
        ),
        List(Create, Delete),
        Reference(
          "authorizors",
          Some(
            Reference(
              "g",
              None,
              Some(PositionRange(Position(85, 3, 48), Position(86, 3, 49)))
            )
          ),
          Some(PositionRange(Position(73, 3, 36), Position(86, 3, 49)))
        ),
        Some(PositionRange(Position(37, 2, 37), Position(86, 3, 49)))
      )
    )
    assert(parsedPermissions == expected)
  }
}
