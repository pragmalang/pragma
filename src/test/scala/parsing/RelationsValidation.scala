package parsing

import org.scalatest.FlatSpec
import domain.SyntaxTree
import domain.PositionRange
import org.parboiled2.Position
import scala.util.Failure
import domain.utils.UserError

class RelationsValidation extends FlatSpec {
  "Validator" should "validate relations specified in `@relation` directives correctly" in {
    val code = """
        @user model User {
            username: String @publicCredential
            password: String @secretCredential
            todos: [Todo] @relation(name: "edits")
            doneTodos: [Todo]
            adminOf: Todo? @relation(name: "adminOf")
            favoriteTodo: Todo?
        }


        model Todo {
            editors: User @relation(name: "edits")
            admin: User @relation(name: "adminOf1")
        }
        """

    val st = SyntaxTree.from(code)
    val expected = Failure(
      UserError(
        List(
          (
            "The fields `todos` and `editors` must both be arrays in case of many-to-many relations, or both non-arrays in case of one-to-one relations",
            Some(PositionRange(Position(134, 5, 13), Position(139, 5, 18)))
          ),
          (
            "A field of type `User` must exist with `@relation(\"adminOf\")` on Todo",
            Some(PositionRange(Position(215, 7, 13), Position(222, 7, 20)))
          ),
          (
            "The fields `editors` and `todos` must both be arrays in case of many-to-many relations, or both non-arrays in case of one-to-one relations",
            Some(PositionRange(Position(334, 13, 13), Position(341, 13, 20)))
          ),
          (
            "A field of type `Todo` must exist with `@relation(\"adminOf1\")` on User",
            Some(PositionRange(Position(385, 14, 13), Position(390, 14, 18)))
          )
        )
      )
    )

    assert(st == expected)
  }
}
