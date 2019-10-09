import org.scalatest._
import domain._
import domain.primitives._
import parsing._
import scala.util._
import scala.collection.immutable.ListMap
import org.parboiled2.Position

class ModelParsing extends FlatSpec {
  "Model parser" should "successfully return a model" in {
    val code = """
      model User {
          username: String,
          age: Integer?,
          todos: [Todo],
      }
      """
    val parsedModel = new HeavenlyParser(code).modelDef.run()
    val exprected = Success(
      HModel(
        "User",
        List(
          HModelField(
            "username",
            HString,
            None,
            List(),
            Some(PositionRange(Position(30, 3, 11), Position(38, 3, 19)))
          ),
          HModelField(
            "age",
            HOption(HInteger),
            None,
            List(),
            Some(PositionRange(Position(58, 4, 11), Position(61, 4, 14)))
          ),
          HModelField(
            "todos",
            HArray(HReference("Todo")),
            None,
            List(),
            Some(PositionRange(Position(83, 5, 11), Position(88, 5, 16)))
          )
        ),
        List(),
        Some(PositionRange(Position(13, 2, 13), Position(17, 2, 17)))
      )
    )
    assert(parsedModel == exprected)
  }

  "Directives" should "be parsed correctrly" in {
    val code = """
      @user
      @validate(validator: "Some Function")
      model User {
        @secretCredential
        username: String,

        age: Integer = 20
      }
    """
    val parsedModel = new HeavenlyParser(code).modelDef.run()
    val expected = Success(
      HModel(
        "User",
        List(
          HModelField(
            "username",
            HString,
            None,
            List(
              FieldDirective(
                "secretCredential",
                HInterfaceValue(ListMap(), HInterface("", List(), None)),
                Some(PositionRange(Position(84, 5, 9), Position(110, 6, 9)))
              )
            ),
            Some(PositionRange(Position(110, 6, 9), Position(118, 6, 17)))
          ),
          HModelField(
            "age",
            HInteger,
            Some(HIntegerValue(20)),
            List(),
            Some(PositionRange(Position(137, 8, 9), Position(140, 8, 12)))
          )
        ),
        List(
          ModelDirective(
            "user",
            HInterfaceValue(ListMap(), HInterface("", List(), None)),
            Some(PositionRange(Position(7, 2, 7), Position(19, 3, 7)))
          ),
          ModelDirective(
            "validate",
            HInterfaceValue(
              ListMap("validator" -> HStringValue("Some Function")),
              HInterface("", List(), None)
            ),
            Some(PositionRange(Position(19, 3, 7), Position(63, 4, 7)))
          )
        ),
        Some(PositionRange(Position(69, 4, 13), Position(73, 4, 17)))
      )
    )
    assert(parsedModel == expected)
  }
}
