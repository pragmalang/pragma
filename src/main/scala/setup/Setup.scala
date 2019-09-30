package setup
import domain.SyntaxTree
import sangria.schema._
import scala.util.Try
import sangria.parser.QueryParser
import scala.language.implicitConversions
import sangria.execution.Executor
import sangria.schema.Schema
import sangria.renderer.{SchemaRenderer, SchemaFilter}
import scala.util.{Success, Failure}

trait Migrator {
  def apply(schema: Schema[Any, Any]): Try[Unit]
}

object Setup {

  implicit def parseQuery(query: String) = QueryParser.parse(query)

  def syntaxTreeToGraphQlSchema(
      syntaxTree: SyntaxTree,
      queryType: ObjectType[Any, Any] = ObjectType[Any, Any]("Query", Nil),
      mutationType: ObjectType[Any, Any] = ObjectType[Any, Any]("Mutation", Nil),
      subscriptionType: ObjectType[Any, Any] = ObjectType[Any, Any]("Subscription", Nil)
  ) = {
    // Mock schema AST
    val schemaAst = Schema(
      query = queryType,
      mutation = Some(mutationType),
      subscription = Some(subscriptionType)
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
      "type Query { id: String } type User { name: String!, age: Int! }"
    )
    schemaAst match {
      case Success(value) =>
        println(
          SchemaRenderer.renderSchema(
            Schema.buildFromAst(value),
            SchemaFilter(
              typeName =>
                !Schema.isBuiltInType(typeName) && typeName != "Query",
              dirName => !Schema.isBuiltInDirective(dirName)
            )
          )
        )
      case Failure(_) => println("Error")
    }
  }
}

/*
val st = ???
val fakeSchema = toGraphQLSchema(st)
val stringSchema = SchemaRenderer.renderSchema(fakeSchema)
val realSchema = parseSchema(stringSchema)

schema {
  query: Hello
}

type Hello {
  bar: Bar
}

type Bar {
  isColor: Boolean
}

 */
