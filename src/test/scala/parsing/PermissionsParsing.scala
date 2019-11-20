import org.scalatest._
import domain._, primitives._
import parsing._, HeavenlyParser._
import scala.util._
import org.parboiled2.Position
import scala.collection.immutable.ListMap

class PermissionsParsing extends FlatSpec {
  "Permissions" should "be parsed correctly" in {
    val code = """
      permit {
          ALL Book authorizors.f
          [CREATE, DELETE] Todo authorizors.g
      }
      """
    val parsedPermissions = new HeavenlyParser(code).permitDef.run()
    val expected = Success(
      Permissions(
        Tenant(
          "*",
          List(
            GlobalRule(
              ShapeResource(
                ResourceReference(
                  "Book",
                  None,
                  Some(PositionRange(Position(30, 3, 15), Position(34, 3, 19)))
                )
              ),
              List(Create, Read, Update, Delete),
              HFunctionValue(
                MemberExpression(
                  ReferenceExpression(
                    "authorizors",
                    Some(
                      PositionRange(Position(35, 3, 20), Position(46, 3, 31))
                    )
                  ),
                  "f",
                  Some(PositionRange(Position(35, 3, 20), Position(48, 3, 33)))
                ),
                HFunction(ListMap(), HReference("f"))
              ),
              Some(PositionRange(Position(26, 3, 11), Position(48, 3, 33)))
            ),
            GlobalRule(
              ShapeResource(
                ResourceReference(
                  "Todo",
                  None,
                  Some(PositionRange(Position(76, 4, 28), Position(80, 4, 32)))
                )
              ),
              List(Create, Delete),
              HFunctionValue(
                MemberExpression(
                  ReferenceExpression(
                    "authorizors",
                    Some(
                      PositionRange(Position(81, 4, 33), Position(92, 4, 44))
                    )
                  ),
                  "g",
                  Some(PositionRange(Position(81, 4, 33), Position(94, 4, 46)))
                ),
                HFunction(ListMap(), HReference("g"))
              ),
              Some(PositionRange(Position(48, 3, 33), Position(94, 4, 46)))
            )
          ),
          None
        ),
        Nil,
        Some(PositionRange(Position(26, 3, 11), Position(94, 4, 46)))
      )
    )
    assert(parsedPermissions == expected)
  }
}
