import org.scalatest._
import domain._
import domain.primitives._
import parsing._
import scala.util._

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
        id = "User",
        fields = List(
          HModelField("username", HString, Nil, false),
          HModelField("age", HOption(HInteger), Nil, true),
          HModelField("todos", HArray(HModel("Todo", Nil, Nil)), Nil, false)
        ),
        directives = Nil
      )
    )
    assert(parsedModel == exprected)
  }
}
