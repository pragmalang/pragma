import org.scalatest._
import domain._, primitives._
import parsing._
import scala.util._
import org.parboiled2.Position

class ModelFieldTypeSubstitution extends FlatSpec {
  "Substitutor" should "substitute model field types with the defined type if found" in {
    val code = """
      enum Gender {
          Male
          Female
      }

      model User {
          username: String
          password: String
          todo: Todo
          gender: Gender
      }

      model Todo {
          title: String
          content: String
      }
      """
    val syntaxTree = new HeavenlyParser(code).syntaxTree.run().get
    val substited = new Substitutor(syntaxTree).run
    val expected = Success(
      List(
        HEnum(
          "Gender",
          List("Male", "Female"),
          Some(PositionRange(Position(12, 2, 12), Position(18, 2, 18)))
        ),
        HModel(
          "User",
          List(
            HModelField(
              "username",
              HString,
              None,
              List(),
              false,
              Some(PositionRange(Position(91, 8, 11), Position(99, 8, 19)))
            ),
            HModelField(
              "password",
              HString,
              None,
              List(),
              false,
              Some(PositionRange(Position(118, 9, 11), Position(126, 9, 19)))
            ),
            HModelField(
              "todo",
              HModel(
                "Todo",
                List(
                  HModelField(
                    "title",
                    HString,
                    None,
                    List(),
                    false,
                    Some(
                      PositionRange(
                        Position(219, 15, 11),
                        Position(224, 15, 16)
                      )
                    )
                  ),
                  HModelField(
                    "content",
                    HString,
                    None,
                    List(),
                    false,
                    Some(
                      PositionRange(
                        Position(243, 16, 11),
                        Position(250, 16, 18)
                      )
                    )
                  )
                ),
                List(),
                Some(
                  PositionRange(Position(202, 14, 13), Position(206, 14, 17))
                )
              ),
              None,
              List(),
              false,
              Some(PositionRange(Position(145, 10, 11), Position(149, 10, 15)))
            ),
            HModelField(
              "gender",
              HEnum(
                "Gender",
                List("Male", "Female"),
                Some(
                  PositionRange(Position(12, 2, 12), Position(18, 2, 18))
                )
              ),
              None,
              List(),
              false,
              Some(PositionRange(Position(166, 11, 11), Position(172, 11, 17)))
            )
          ),
          List(),
          Some(PositionRange(Position(74, 7, 13), Position(78, 7, 17)))
        ),
        HModel(
          "Todo",
          List(
            HModelField(
              "title",
              HString,
              None,
              List(),
              false,
              Some(PositionRange(Position(219, 15, 11), Position(224, 15, 16)))
            ),
            HModelField(
              "content",
              HString,
              None,
              List(),
              false,
              Some(PositionRange(Position(243, 16, 11), Position(250, 16, 18)))
            )
          ),
          List(),
          Some(PositionRange(Position(202, 14, 13), Position(206, 14, 17)))
        )
      )
    )

    assert(substited == expected)
  }
}
