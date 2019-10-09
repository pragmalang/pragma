import org.scalatest._
import domain._, primitives._, utils._
import parsing._
import scala.util._
import org.parboiled2.Position

class Validation extends FlatSpec {
  "Default field value checker" should "fail in case of type mismatch" in {
    val code = """
      model User {
          name: String = "John Doe"
          age: Integer = "Not an Integer"
          petName: String? = "Fluffykins"
          friends: [String] = ["John", "James", "Jane"]
          favoriteNumbers: [Float] = [1.0, 42.0, "TEXT", 2]
          invalidOptional: String? = 42
          someEmptyArray: [Float] = []
      }
      """
    val st = new HeavenlyParser(code).syntaxTree.run().get
    val validator = new Validator(st)
    val expectedErrors = List(
      "Invalid default value of type `String` for field `age` of type `Integer`",
      "Invalid values for array field `favoriteNumbers` (all array elements must have the same type)",
      "Invalid default value of type `Integer` for optional field `invalidOptional` of type `String?`"
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
}
