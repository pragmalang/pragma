import org.scalatest._
import domain._, utils._
import parsing._
import scala.util._

class Validation extends FlatSpec {
  "Default field value checker" should "fail in case of type mismatch" in {
    val code = """
      @1 model User {
          name: String = "John Doe"
          age: Int = "Not an Integer"
          petName: String? = "Fluffykins"
          friends: [String] = ["John", "James", "Jane"]
          favoriteNumbers: [Float] = [1.0, 42.0, "TEXT", 2]
          invalidOptional: String? = 42
          someEmptyArray: [Float] = []
      }
      """
    val st = new PragmaParser(code).syntaxTree.run().get
    val validator = new Validator(st)
    val expectedErrors = List(
      "Invalid default value of type `String` for field `age` of type `Int`",
      "Invalid values for array field `favoriteNumbers` (all array elements must have the same type)",
      "Invalid default value of type `Int` for optional field `invalidOptional` of type `String?`"
    )
    validator.checkFieldValueType match {
      case Failure(e: UserError) =>
        assert(expectedErrors == e.errors.map(_._1))
      case _ =>
        throw new Exception(
          "Type checking should fail for mutliple reasons, but it ditn't"
        )
    }
  }

  "Roles defined for non-user models" should "not be allowed" in {
    val code = """
    @1 model Todo {
      title: String
      content: String
    }

    role Todo {
      allow ALL self
    }
    """
    val syntaxTree = SyntaxTree.from(code)
    val expected = Failure(
      UserError(
        List(
          (
            "Roles can only be defined for user models, but `Todo` is not a defined user model",
            None
          )
        )
      )
    )
    assert(syntaxTree == expected)
  }

  "Non-existant field types" should "not be allowed" in {
    val code = """
    @1 @user model User {
      username: String @publicCredential
      todos: [Todof]
    }

    @2 model Todo {
      title: Stringf
      content: String
      user: User
    }
    """
    val syntaxTree = SyntaxTree.from(code)
    val expected = List(
      "Type `Todof` is not defined",
      "Type `Stringf` is not defined"
    )
    syntaxTree match {
      case Failure(err: UserError) =>
        assert(err.errors.map(_._1) == expected)

      case _ => fail("Result must be an error")
    }
  }
}
