package running.pipeline.functions

import running.pipeline._
import scala.util.{Try, Success, Failure}
import domain.SyntaxTree, domain.utils.typeCheckJson
import sangria.ast._
import sangria.validation.Violation
import sangria.validation.QueryValidator
import sangria.schema.Schema
import setup.schemaGenerator.ApiSchemaGenerator
import spray.json._, DefaultJsonProtocol._
import running.Implicits._
import running.errors._
import domain.HReference

case class RequestValidator(syntaxTree: SyntaxTree)
    extends PiplineFunction[Request, Try[Request]] {

  override def apply(input: Request): Try[Request] = Try {
    val newCtx = validateQuery(input.ctx).get
    input.copy(ctx = newCtx)
  }

  val queryValidator = QueryValidator.default

  val apiSchemaGenerator = ApiSchemaGenerator
    .default(syntaxTree)

  val apiSchemaSyntaxTree: SyntaxTree =
    apiSchemaGenerator.buildApiSchemaAsSyntaxTree

  val sangriaSchema =
    Schema.buildFromAst(apiSchemaGenerator.buildApiSchemaAsDocument)

  def validateQuery(ctx: RequestContext): Try[RequestContext] = Try {
    val violations = queryValidator
      .validateQuery(sangriaSchema, ctx.query)
      .toList

    if (!violations.isEmpty)
      throw new QueryError(violations.map(_.errorMessage).mkString(","))

    val operationDefinitions = ctx.query.definitions
      .collect { case d: OperationDefinition => d }

    operationDefinitions.size match {
      case 1 =>
        ctx.copy(
          queryVariables = Left(
            coerceVariables(
              operationDefinitions.head,
              ctx.queryVariables.swap.getOrElse(JsObject.empty)
            ).get
          )
        )
      case _ =>
        ctx.copy(
          queryVariables = Right(
            operationDefinitions
              .zip(ctx.queryVariables.getOrElse(Nil))
              .map(op => coerceVariables(op._1, op._2).get)
              .toList
          )
        )
    }
  }

  def isInputType(tpe: Type, syntaxTree: SyntaxTree): Try[Unit] = Try {
    val typeFromSchema = apiSchemaGenerator.getTypeFromSchema(tpe).get.typeDef
    typeFromSchema match {
      case _: InputObjectTypeDefinition => ()
      case _: EnumTypeDefinition        => ()
      case _: ScalarTypeDefinition      => ()
      case _ =>
        throw new Exception(s"${tpe.namedType.name} is not an Input type")
    }
  }

  // This is an exact implementation of the algorithim in the GraphQL spec
  // https://graphql.github.io/graphql-spec/June2018/#sec-Coercing-Variable-Values
  def coerceVariables(
      operationDefinition: OperationDefinition,
      variables: JsObject
  ) = Try {
    val coercedValues = collection.mutable.Map.empty[String, JsValue]
    val variableDefinitions = operationDefinition.variables
    for {
      variableDefinition <- variableDefinitions
    } {
      val variableName = variableDefinition.name
      val variableType = variableDefinition.tpe
      isInputType(variableType, syntaxTree).get
      val defaultValue = variableDefinition.defaultValue
      val hasValue =
        variables.fields.exists(f => f._1 == variableName)
      val value = variables.fields(variableName)
      if (hasValue && defaultValue.isDefined)
        coercedValues.addOne(variableName -> defaultValue.get.toJson)
      else if (variableType
                 .isInstanceOf[NotNullType] && (!hasValue || value == JsNull))
        throw new QueryError("Variables coercion failed")
      else if (hasValue) {
        if (typeCheckJson(
              HReference(variableType.namedType.name),
              apiSchemaSyntaxTree
            )(
              value
            ).isFailure)
          throw new QueryError("Variables coercion failed")
        else coercedValues.addOne(variableName -> value)
      }
    }
    JsObject(Map.from(coercedValues))
  }
}
