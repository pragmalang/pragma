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
        @publicCredential
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
                "publicCredential",
                HInterfaceValue(ListMap(), HInterface("", List(), None)),
                Some(PositionRange(Position(84, 5, 9), Position(101, 5, 26)))
              )
            ),
            Some(PositionRange(Position(110, 6, 9), Position(118, 6, 17)))
          ),
          HModelField(
            "age",
            HInteger,
            Some(HIntegerValue(20L)),
            List(),
            Some(PositionRange(Position(137, 8, 9), Position(140, 8, 12)))
          )
        ),
        List(
          ModelDirective(
            "user",
            HInterfaceValue(ListMap(), HInterface("", List(), None)),
            Some(PositionRange(Position(7, 2, 7), Position(12, 2, 12)))
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

  "Trailing model field directives" should "be parsed correctly" in {
    val code = """
    model User {
      username: String @publicCredenticl
      password: String @secretCredential
    }
    """
    val parsedModel = new HeavenlyParser(code).syntaxTree.run()
    val expected = Success(
      List(
        HModel(
          "User",
          List(
            HModelField(
              "username",
              HString,
              None,
              List(
                FieldDirective(
                  "publicCredenticl",
                  HInterfaceValue(ListMap(), HInterface("", List(), None)),
                  Some(PositionRange(Position(41, 3, 24), Position(58, 3, 41)))
                )
              ),
              Some(PositionRange(Position(24, 3, 7), Position(32, 3, 15)))
            ),
            HModelField(
              "password",
              HString,
              None,
              List(
                FieldDirective(
                  "secretCredential",
                  HInterfaceValue(ListMap(), HInterface("", List(), None)),
                  Some(PositionRange(Position(82, 4, 24), Position(99, 4, 41)))
                )
              ),
              Some(PositionRange(Position(65, 4, 7), Position(73, 4, 15)))
            )
          ),
          List(),
          Some(PositionRange(Position(11, 2, 11), Position(15, 2, 15)))
        )
      )
    )
    assert(parsedModel == expected)
  }

  "Multiple inline directives" should "be parsed correctly" in {
    val code = """
    @user
    model User {
      id: String @id @primary
      name: String
    }
    """
    val parsedModel = new HeavenlyParser(code).modelDef.run()
    val expected = Success(
      HModel(
        "User",
        List(
          HModelField(
            "id",
            HString,
            None,
            List(
              FieldDirective(
                "id",
                HInterfaceValue(ListMap(), HInterface("", List(), None)),
                Some(PositionRange(Position(45, 4, 18), Position(48, 4, 21)))
              ),
              FieldDirective(
                "primary",
                HInterfaceValue(ListMap(), HInterface("", List(), None)),
                Some(PositionRange(Position(49, 4, 22), Position(57, 4, 30)))
              )
            ),
            Some(PositionRange(Position(34, 4, 7), Position(36, 4, 9)))
          ),
          HModelField(
            "name",
            HString,
            None,
            List(),
            Some(PositionRange(Position(64, 5, 7), Position(68, 5, 11)))
          )
        ),
        List(
          ModelDirective(
            "user",
            HInterfaceValue(ListMap(), HInterface("", List(), None)),
            Some(PositionRange(Position(5, 2, 5), Position(10, 2, 10)))
          )
        ),
        Some(PositionRange(Position(21, 3, 11), Position(25, 3, 15)))
      )
    )
    assert(expected == parsedModel)
  }
}
