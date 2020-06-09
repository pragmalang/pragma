package parsing

import org.scalatest.FlatSpec
import domain.SyntaxTree
import scala.util.Failure
import domain.utils.UserError

class RelationsValidation extends FlatSpec {
  "Validator" should "validate relations specified in `@relation` directives correctly" in {
    val code = """
        @1 @user
        model User {
            @1 username: String @publicCredential @primary
            @2 password: String @secretCredential
            @3 todos: [Todo] @relation(name: "edits")
            @4 doneTodos: [Todo]
            @5 adminOf: Todo? @relation(name: "adminOf")
            @6 favoriteTodo: Todo?
        }


        @2
        model Todo {
          @1 editors: User @relation(name: "edits")
          @2 admin: User @relation(name: "adminOf1")
          @3 id: String @uuid @primary
        }
        """

    val st = SyntaxTree.from(code)
    val expectedErrors = List(
      "The fields `todos` and `editors` must both be arrays in case of many-to-many relations, or both non-arrays in case of one-to-one relations",
      "A field of type `User` must exist with `@relation(\"adminOf\")` on Todo",
      "The fields `editors` and `todos` must both be arrays in case of many-to-many relations, or both non-arrays in case of one-to-one relations",
      "A field of type `Todo` must exist with `@relation(\"adminOf1\")` on User"
    )

    st match {
      case Failure(err: UserError) =>
        assert(err.errors.map(_._1) == expectedErrors)
      case _ => fail("Validation should fail with the expected errors")
    }
  }
}
