import domain._
import sangria.ast._
import sangria.schema._
import sangria.renderer.SchemaRenderer
import sangria.macros._
import sangria.execution.Executor
import sangria.validation.QueryValidator
object Main extends App {
  val schema = Schema.buildFromAst(
    Document(
      Vector(
        ObjectTypeDefinition(
          name = "Query",
          interfaces = Vector.empty,
          fields = Vector(FieldDefinition("stub", NamedType("A"), Vector.empty))
        ),
        ObjectTypeDefinition(
          name = "A",
          interfaces = Vector.empty,
          fields =
            Vector(FieldDefinition("stub", NamedType("String"), Vector.empty))
        )
      )
    )
  )
  val renderedSchema = SchemaRenderer.renderSchema(schema)
  println(renderedSchema)
  println(schema.astNodes)
  val query =
    gql"""
    {
      stub {
          stub
      }
    }
    """
  println(QueryValidator.default.validateQuery(schema, query))
}
