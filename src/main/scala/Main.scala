import domain._

import sangria.ast._
import sangria.macros._
import pprint._
import running.pipeline.functions.RequestValidator
import parsing.HeavenlyParser
import spray.json._, DefaultJsonProtocol._
import domain.utils.typeCheckJson
object Main extends App {
  val code = """
  model User {
    id: String @id @primary
    name: String
  }
  """

  val parsed = SyntaxTree.from(code).get

  val validator = RequestValidator(parsed)
  val json = JsObject(
    "id" -> "123".toJson,
    "name" -> "anas".toJson
  )

  val userTypeFromSchema = validator.getTypeFromSchema(NamedType("User"))

  
  val validationResult = typeCheckJson(HReference("User"), parsed)(json).get
  pprintln(validationResult.prettyPrint)
}
