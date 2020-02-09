import org.scalatest._
import domain._, primitives._
import parsing._, HeavenlyParser._
import scala.util._
import org.parboiled2.Position
import scala.collection.immutable.ListMap

class PermissionsParsing extends FlatSpec {
  "Permissions" should "be parsed correctly" in {
    val code = """
      acl {
          allow ALL Book authorizors.f
          deny [CREATE, DELETE] Todo authorizors.g
      }
      """
    val parsedPermissions = new HeavenlyParser(code).aclDef.run()

    val expected = Permissions(
      Tenant(
        "root",
        List(
          AccessRule(
            Allow,
            ShapeResource(
              ResourceReference(
                "Book",
                None,
                Some(PositionRange(Position(33, 3, 21), Position(37, 3, 25)))
              )
            ),
            List(Create, Read, Update, Delete),
            Reference(
              "authorizors",
              Some(
                Reference(
                  "f",
                  None,
                  Some(PositionRange(Position(50, 3, 38), Position(51, 3, 39)))
                )
              ),
              Some(PositionRange(Position(38, 3, 26), Position(51, 3, 39)))
            ),
            Some(PositionRange(Position(23, 3, 11), Position(51, 3, 39)))
          ),
          AccessRule(
            Deny,
            ShapeResource(
              ResourceReference(
                "Todo",
                None,
                Some(PositionRange(Position(84, 4, 33), Position(88, 4, 37)))
              )
            ),
            List(Create, Delete),
            Reference(
              "authorizors",
              Some(
                Reference(
                  "g",
                  None,
                  Some(
                    PositionRange(Position(101, 4, 50), Position(102, 4, 51))
                  )
                )
              ),
              Some(PositionRange(Position(89, 4, 38), Position(102, 4, 51)))
            ),
            Some(PositionRange(Position(51, 3, 39), Position(102, 4, 51)))
          )
        ),
        List(),
        None
      ),
      List(),
      Some(PositionRange(Position(23, 3, 11), Position(102, 4, 51)))
    )
    assert(parsedPermissions.get == expected)
  }
}
