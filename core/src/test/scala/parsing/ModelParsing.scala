package pragma.parsing

import pragma.domain._, pragma.parsing._
import org.scalatest.flatspec.AnyFlatSpec

class ModelParsing extends AnyFlatSpec {
  "Model parser" should "successfully return a model" in {
    val code =
      """@1 model User {
          username: String @primary @1,
          age: Int? @2,
          todos: [Todo] @3
      }"""
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
    val code =
      """@user
      @validate(validator: "Some Function")
      @1
      model User {
        @1 @publicCredential
        @primary
        username: String,

        @2 age: Int = 20
      }"""

    val parsedModel = new PragmaParser(code).modelDef.run().get
    val mdirs = parsedModel.directives

    assert(mdirs(0).id == "user")
    assert(mdirs(1).args.value("validator") == PStringValue("Some Function"))
  }

  "Trailing model field directives" should "be parsed correctly" in {
    val code =
      """@1 model User {
             @1 username: String @publicCredenticl @primary
             @2 password: String @secretCredential
         }"""
    val parsedModel = new PragmaParser(code).modelDef.run().get

    assert(parsedModel.fields(0).directives(0).id == "publicCredenticl")
    assert(parsedModel.fields(1).directives(0).id == "secretCredential")
  }

  "Multiple inline directives" should "be parsed correctly" in {
    val code =
      """@1 @user
         model User {
           @1 id: String @id @primary
           name: String @2
         }"""
    val parsedModel = new PragmaParser(code).modelDef.run().get

    assert(parsedModel.directives.length == 1)
    assert(parsedModel.fields(0).directives.length == 2)
    assert(parsedModel.fields(1).directives.length == 0)
  }
}
