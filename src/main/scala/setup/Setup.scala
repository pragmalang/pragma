package setup
import domain.SyntaxTree
import sangria.schema._
import scala.util.Try
import sangria.parser.QueryParser
import scala.language.implicitConversions
import sangria.execution.Executor
import sangria.schema.Schema
import sangria.renderer.{SchemaRenderer, SchemaFilter}
import sangria.schema.StringType
import scala.util.{Success, Failure}

trait Migrator {
  def apply(schema: Schema[Any, Any]): Try[Unit]
}

object Setup {
  case class GraphQlDefinitionsIR(
      query: ObjectType[Any, Any],
      mutation: Option[ObjectType[Any, Any]] = None,
      subscription: Option[ObjectType[Any, Any]] = None,
      additionalTypes: List[Type with Named] = Nil,
      directives: List[Directive] = BuiltinDirectives
  )
  implicit def parseQuery(query: String) = QueryParser.parse(query)

  def syntaxTreeToGraphQlSchema(
      syntaxTree: SyntaxTree,
      queryType: ObjectType[Any, Any] = ObjectType(
        "Query",
        fields[Any, Any](
          Field("query", StringType, resolve = _ => "")
        )
      ),
      mutationType: Option[ObjectType[Any, Any]] = None,
      subscriptionType: Option[ObjectType[Any, Any]] = None
  ) = {
    val definitions = GraphQlDefinitionsIR(
      query = queryType,
      mutation = mutationType,
      subscription = subscriptionType
    )
    // Mock schema AST
    val schemaAst = Schema(
      query = definitions.query,
      mutation = definitions.mutation,
      subscription = definitions.subscription
    ).toAst
    Schema.buildFromAst(schemaAst)
  }

  def apply(
      syntaxTree: SyntaxTree,
      migrator: Migrator
  ) = {
    migrator(syntaxTreeToGraphQlSchema(syntaxTree))
    apiSchema(syntaxTree)
  }

  def apiSchema(syntaxTree: SyntaxTree): Schema[Any, Any] = ???

  def executor(schema: Schema[Any, Any]) = (query: String) => ???

  def dummyDemo = {
    val schemaAst = QueryParser.parse(
      "type User { name: String!, age: Int! }"
    )
    schemaAst match {
      case Success(value) =>
        Schema
          .buildDefinitions(value)
          .map(_ match {
            case ot: ObjectType[_, _] => ot.fieldsFn().map(_.name).toString
            case t => t.toString
          })
      case Failure(e) => s"Error: ${e.getMessage()}"
    }
  }
}
