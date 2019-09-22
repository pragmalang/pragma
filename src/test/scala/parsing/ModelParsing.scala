import org.scalatest._
import domain._
import domain.primitives._
import parsing._
import scala.util._
import scala.collection.immutable.ListMap

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
          HModelField("username", HString, None, Nil, false),
          HModelField("age", HOption(HInteger), None, Nil, true),
          HModelField(
            "todos",
            HArray(HModel("Todo", Nil, Nil)),
            None,
            Nil,
            false
          )
        ),
        directives = Nil
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
                HInterfaceValue(ListMap(), HInterface("", Nil))
              )
            ),
            false
          ),
          HModelField("age", HInteger, Some(HIntegerValue(20)), Nil, false)
        ),
        List(
          ModelDirective(
            "user",
            HInterfaceValue(ListMap(), HInterface("", Nil))
          ),
          ModelDirective(
            "validate",
            HInterfaceValue(
              ListMap("validator" -> HStringValue("Some Function")),
              HInterface("", Nil)
            )
          )
        )
      )
    )
    assert(parsedModel == expected)
  }
}
