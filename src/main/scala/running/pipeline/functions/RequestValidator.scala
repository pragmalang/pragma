package running.pipeline.functions

import running.pipeline._
import scala.util.Try
import domain.SyntaxTree, domain.utils.typeCheckJson
import sangria.ast._
import sangria.validation.QueryValidator
import sangria.schema.Schema
import setup.schemaGenerator.ApiSchemaGenerator
import setup.schemaGenerator.Implicits._
import spray.json._
import running.Implicits._
import running.errors._
import akka.stream.scaladsl.Source

case class RequestValidator(syntaxTree: SyntaxTree) {

  def apply(input: Request): Source[Request, _] =
    Source.fromIterator { () =>
      Iterator(validateQuery(input).get)
    }

  val queryValidator = QueryValidator.default

  val apiSchemaGenerator = ApiSchemaGenerator(syntaxTree)

  val sangriaSchema =
    Schema.buildFromAst(apiSchemaGenerator.buildApiSchemaAsDocument)

  def validateQuery(request: Request): Try[Request] = Try {
    val violations = queryValidator
      .validateQuery(sangriaSchema, request.query)
      .toList

    if (!violations.isEmpty)
      throw new QueryError(violations.map(_.errorMessage).mkString(","))

    val operationDefinitions = request.query.definitions
      .collect { case d: OperationDefinition => d }

    operationDefinitions.size match {
      case 1 =>
        request.copy(
          queryVariables = Left(
            coerceVariables(
              operationDefinitions.head,
              request.queryVariables.swap.getOrElse(JsObject.empty)
            ).get
          )
        )
      case _ =>
        request.copy(
          queryVariables = Right(
            operationDefinitions
              .zip(request.queryVariables.getOrElse(Nil))
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
              fromGraphQLNamedTypeToHType(variableType.namedType),
              apiSchemaGenerator.buildApiSchemaAsSyntaxTree
            )(
              value
            ).isFailure)
          throw new QueryError("Variables coercion failed")
        else coercedValues.addOne(variableName -> value)
      }
    }
    JsObject(Map.from(coercedValues))
  }

  def coerceArgumentValues(
      objectType: ObjectTypeDefinition,
      field: Field,
      variables: JsObject
  ) = Try {
    val coercedValues = collection.mutable.Map.empty[String, JsValue]
    val argumentValues = field.arguments
    val fieldName = field.name
    val argumentDefinitions =
      objectType.fields.find(_.name == fieldName).get.arguments

    for { argumentDefinition <- argumentDefinitions } {
      val argumentName = argumentDefinition.name
      val argumentType = argumentDefinition.valueType
      val defaultValue = argumentDefinition.defaultValue
      val argumentValue = argumentValues.find(_.name == argumentName)
      var hasValue = argumentValue.isDefined
      var value: Option[JsValue] = None
      if (argumentValue.get.value.isInstanceOf[VariableValue]) {
        val variableName = argumentValue.get.name
        hasValue = variables.fields.exists(_._1 == variableName)
        value = variables.fields.find(_._1 == variableName).map(_._2)
      } else {
        value = argumentValue.get.value.toJson.asJsObject.fields.get("value")
      }
      if (!hasValue && defaultValue.isDefined) {
        coercedValues.addOne(argumentName -> defaultValue.get.toJson)
      } else if (argumentType
                   .isInstanceOf[NotNullType] & (!hasValue || value.get == JsNull)) {
        throw new QueryError("Variables coercion failed")
      } else if (hasValue) {
        if (value.get == JsNull)
          coercedValues.addOne(argumentName -> JsNull)
        else if (argumentValue.get.value.isInstanceOf[VariableValue])
          coercedValues.addOne(argumentName -> argumentValue.get.value.toJson)
        else {
          val coercedValue = typeCheckJson(
            fromGraphQLNamedTypeToHType(argumentType.namedType),
            apiSchemaGenerator.buildApiSchemaAsSyntaxTree
          )(value.get)
          if (coercedValue.isFailure)
            throw new QueryError("Variables coercion failed")
          else coercedValues.addOne(argumentName -> coercedValue.get)
        }
      }
    }
    JsObject(Map.from(coercedValues))
  }
}
