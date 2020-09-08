package running

import scala.util.Try
import domain.SyntaxTree, domain.utils.typeCheckJson
import sangria.ast._
import sangria.validation.QueryValidator
import sangria.schema.Schema
import setup.schemaGenerator.ApiSchemaGenerator
import setup.schemaGenerator.SchemaGeneratorImplicits._
import spray.json._
import RunningImplicits._
import cats.implicits._
import scala.util.Failure
import scala.util.Success
import running.utils.QueryError

class RequestValidator(syntaxTree: SyntaxTree) {

  def apply(input: Request): Try[Request] =
    validateQuery(input)

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

    operationDefinitions match {
      case Vector(op) =>
        coerceVariables(op, request.queryVariables) match {
          case Failure(exception) => throw exception
          case Success(_)         => request
        }
      case _ => {
        operationDefinitions.map { op =>
          coerceVariables(
            op,
            request.queryVariables
              .fields(op.name.get)
              .asJsObject("Query variables must be sent as a JSON object")
          )
        //   map { variables =>
        //     for {
        //       op <- operationDefinitions
        //       opName = op.name match {
        //         case Some(name) => name
        //         case None => throw new QueryError("Each operation must have a name")
        //       }
        //       opVariables = ???
        //     } yield
        //       opVariables match {
        //         case Some((_, value: JsObject)) => Success(value)
        //         case Some((name, _)) =>
        //           Failure(
        //             new QueryError(
        //               s"Operation `$name` mus"
        //             )
        //           )
        //         case None =>
        //           Failure(
        //             new QueryError(
        //               "Each operation must have a key in the query variables JSON object"
        //             )
        //           )
        //       }
        //     ???
        //   }
        }.sequence match {
          case Failure(exception) => throw exception
          case Success(_)         => request
        }
      }
    }
  }

  def isInputType(tpe: Type): Try[Unit] = Try {
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
      isInputType(variableType).get
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
              fromGraphQLNamedTypeToPType(variableType.namedType),
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
            fromGraphQLNamedTypeToPType(argumentType.namedType),
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
