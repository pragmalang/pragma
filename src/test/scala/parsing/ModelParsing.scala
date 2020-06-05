import org.scalatest._
import domain._
import parsing._
import org.parboiled2.Position

class ModelParsing extends FlatSpec {
  "Model parser" should "successfully return a model" in {
    val code = """
      @1 model User {
          username: String @primary,
          age: Int?,
          todos: [Todo],
      }
      """
    val parsedModel = new PragmaParser(code).modelDef.run().get

    assert(parsedModel.id == "User")

    assert(parsedModel.fields(0).id == "username")
    assert(parsedModel.fields(0).ptype == PString)

    assert(parsedModel.fields(1).id == "age")
    assert(parsedModel.fields(1).ptype == POption(PInt))

    assert(parsedModel.fields(2).id == "todos")
    assert(parsedModel.fields(2).ptype == PArray(PReference("Todo")))
  }

  "Directives" should "be parsed correctrly" in {
    val code = """
      @user
      @validate(validator: "Some Function")
      @1
      model User {
        @publicCredential
        username: String,

        age: Int = 20
      }
    """
    val parsedModel = new PragmaParser(code).modelDef.run().get
    val expectedDirectives = List(
      Directive(
        "user",
        PInterfaceValue(Map(), PInterface("", List(), None)),
        ModelDirective,
        Some(PositionRange(Position(7, 2, 7), Position(12, 2, 12)))
      ),
      Directive(
        "validate",
        PInterfaceValue(
          Map("validator" -> PStringValue("Some Function")),
          PInterface("", List(), None)
        ),
        ModelDirective,
        Some(PositionRange(Position(19, 3, 7), Position(63, 4, 7)))
      )
    )

    assert(parsedModel.directives == expectedDirectives)
  }

  "Trailing model field directives" should "be parsed correctly" in {
    val code = """
    @1 model User {
      username: String @publicCredenticl @primary
      password: String @secretCredential
    }
    """
    val parsedModel = new PragmaParser(code).modelDef.run().get

    assert(parsedModel.fields(0).directives(0).id == "publicCredenticl")
    assert(parsedModel.fields(1).directives(0).id == "secretCredential")
  }

  "Multiple inline directives" should "be parsed correctly" in {
    val code = """
    @1 @user
    model User {
      id: String @id @primary
      name: String
    }
    """
    val parsedModel = new PragmaParser(code).modelDef.run().get

    assert(parsedModel.directives.length == 1)
    assert(parsedModel.fields(0).directives.length == 2)
    assert(parsedModel.fields(1).directives.length == 0)
  }
}
