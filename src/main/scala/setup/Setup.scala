package setup

import domain._
import primitives._
import utils.{TypeMismatchException}
import Implicits._

import sangria.schema._

import scala.util.{Success, Failure, Try}

case class Setup(
    syntaxTree: SyntaxTree,
    migrator: Migrator
) {

  def run = Try(migrator(syntaxTreeToGraphQlSchema(syntaxTree)).get)

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
    val definitions = GraphQlDefinitionsIR(syntaxTree)

    Schema(
      query = queryType,
      mutation = mutationType,
      subscription = subscriptionType,
      additionalTypes = definitions.types
    )
  }

  def apiSchema(syntaxTree: SyntaxTree): Schema[Any, Any] = ???

  def executor(schema: Schema[Any, Any]) = (query: String) => ???

}
