import domain._, utils._
import parsing._
import scala.util._
import org.scalatest.flatspec.AnyFlatSpec

class Validation extends AnyFlatSpec {
  "Default field value checker" should "fail in case of type mismatch" in {
    val code = """
      @1 model User {
          @1 name: String = "John Doe" @primary
          @2 age: Int = "Not an Integer"
          @3 petName: String? = "Fluffykins"
          @4 friends: [String] = ["John", "James", "Jane"]
          @5 favoriteNumbers: [Float] = [1.0, 42.0, "TEXT", 2]
          @6 invalidOptional: String? = 42
          @7 someEmptyArray: [Float] = []
      }
      """
    val st = new PragmaParser(code).syntaxTree.run()
    val validator = new Validator(st.get)
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
      @1 title: String @primary
      @2 content: String
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
      @1 username: String @primary @publicCredential
      @2 todos: [Todof]
    }

    @2 model Todo {
      @1 title: Stringf
      @2 content: String
      @3 user: User
      @4 id: String @primary
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

  "Duplicate model/field indexes" should "result in errors" in {
    val code = """
    @1 @user model User {
      @1 username: String @primary @publicCredential
      @1 password: String @secretCredential
    }

    @2 model Todo {
      @2 title: String @primary
    }

    @1 model Admin {
      @1 username: String @primary
    }
    """

    val st = SyntaxTree.from(code)
    st match {
      case Failure(err: UserError) => {
        val errors = err.errors.map(_._1).toList
        val expectedErrors = List(
          "Model `Admin` has a duplicate index 1",
          "`password` has a duplicate index 1"
        )
        assert(errors == expectedErrors)
      }
      case _ => fail("Should result in duplicate index errors")
    }
  }
}
