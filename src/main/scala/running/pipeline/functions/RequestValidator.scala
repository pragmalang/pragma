package running.pipeline.functions

import running.pipeline._
import scala.util.{Try, Success, Failure}
import domain.SyntaxTree
import sangria.ast._
import sangria.validation.Violation
import sangria.validation.QueryValidator
import sangria.schema.Schema
import setup.schemaGenerator.ApiSchemaGenerator
import spray.json._, DefaultJsonProtocol._
import running.Implicits._
import running.errors._

case class RequestValidator(syntaxTree: SyntaxTree)
    extends PiplineFunction[Request, Try[Request]] {

  override def apply(input: Request): Try[Request] = Try {
    val newCtx = validateQuery(input.ctx).get
    input.copy(ctx = newCtx)
  }

  val queryValidator = QueryValidator.default
  val apiSchema = ApiSchemaGenerator
    .default(syntaxTree)
    .buildApiSchema
  val schema =
    Schema.buildFromAst(apiSchema)

  def validateQuery(ctx: RequestContext): Try[RequestContext] = Try {
    val violations = queryValidator
      .validateQuery(schema, ctx.query)
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

  case class TypeFromSchema(
      typeDef: TypeDefinition,
      isEmptyList: Boolean,
      isNonEmptyList: Boolean,
      isOptional: Boolean,
      tpe: Type
  )

  def getTypeFromSchema(tpe: Type): Option[TypeFromSchema] = Option {
    val td = apiSchema.definitions
      .find({
        case typeDef: TypeDefinition if typeDef.name == tpe.namedType.name =>
          true
        case _ => false
      })
      .map(_.asInstanceOf[TypeDefinition])
      .get

    val isEmptyList = tpe match {
      case ListType(ofType, _) => true
      case _                   => false
    }

    val isNonEmptyList = tpe match {
      case ListType(NotNullType(_, _), _) => true
      case _                              => false
    }

    val isOptional = tpe match {
      case NotNullType(ofType, _) => false
      case _                      => false
    }

    TypeFromSchema(td, isEmptyList, isNonEmptyList, isOptional, tpe)
  }

  def isInputType(tpe: Type, syntaxTree: SyntaxTree): Try[Unit] = Try {
    val typeFromSchema = getTypeFromSchema(tpe).get.typeDef
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
        if (checkJsonAgainstGraphQlType(variableType)(value).isFailure)
          throw new QueryError("Variables coercion failed")
        else coercedValues.addOne(variableName -> value)
      }
    }
    JsObject(Map.from(coercedValues))
  }

  def checkJsonAgainstGraphQlType(td: Type)(json: JsValue): Try[JsValue] =
    Try {
      def fieldsRespectsShape(
          objectFields: Map[String, JsValue],
          typeFields: List[Either[FieldDefinition, InputValueDefinition]]
      ) = {
        val fields = typeFields.map {
          case Left(value)  => value.name -> value.fieldType
          case Right(value) => value.name -> value.valueType
        }
        objectFields.forall(
          of =>
            fields.count(
              tf =>
                tf._1 == of._1 &&
                  checkJsonAgainstGraphQlType(tf._2)(of._2).isSuccess
            ) == 1
        ) && fields
          .filter(
            f =>
              f._2 match {
                case _: NotNullType => true
                case _              => false
              }
          )
          .forall(
            f =>
              objectFields.count(
                of =>
                  f._1 == of._1 &&
                    checkJsonAgainstGraphQlType(f._2)(of._2).isSuccess
              ) == 1
          )
      }
      (getTypeFromSchema(td).get, json) match {
        case (
            t: TypeFromSchema,
            JsNumber(v)
            )
            if t.typeDef.isInstanceOf[ScalarTypeDefinition] && t.typeDef
              .asInstanceOf[ScalarTypeDefinition]
              .name == "Int" &&
              v.isWhole
              && v >= BigDecimal("-2147483648")
              && v < BigDecimal("2147483648") =>
          json
        case (
            t: TypeFromSchema,
            JsNumber(v)
            )
            if t.typeDef.isInstanceOf[ScalarTypeDefinition] && t.typeDef
              .asInstanceOf[ScalarTypeDefinition]
              .name == "Float" =>
          json
        case (
            TypeFromSchema(
              typeDef,
              isEmptyList,
              isNonEmptyList,
              isOptional,
              tpe
            ),
            JsArray(elements)
            )
            if elements
              .map(checkJsonAgainstGraphQlType(tpe)(_))
              .forall {
                case Failure(_) => false
                case Success(_) => true
              } =>
          json

        case (
            TypeFromSchema(
              typeDef,
              isEmptyList,
              true,
              isOptional,
              tpe
            ),
            JsArray(elements)
            )
            if elements
              .map(checkJsonAgainstGraphQlType(tpe)(_))
              .forall {
                case Failure(_) => false
                case Success(_) => true
              } && !elements.isEmpty =>
          json
        case (
            TypeFromSchema(
              ScalarTypeDefinition("Boolean", _, _, _, _),
              _,
              _,
              _,
              _
            ),
            JsBoolean(v)
            ) =>
          json
        case (
            TypeFromSchema(
              _,
              _,
              _,
              true,
              _
            ),
            JsNull
            ) =>
          json
        case (
            TypeFromSchema(
              _,
              _,
              _,
              true,
              typeDef
            ),
            json
            ) =>
          checkJsonAgainstGraphQlType(typeDef)(json).get
        case (
            TypeFromSchema(
              ScalarTypeDefinition("Float", _, _, _, _),
              _,
              _,
              _,
              _
            ),
            JsString(v)
            ) =>
          json
        case (
            TypeFromSchema(
              shape: ObjectTypeDefinition,
              _,
              _,
              _,
              _
            ),
            JsObject(fields)
            )
            if fieldsRespectsShape(fields, shape.fields.map(Left(_)).toList) =>
          json

        case (
            TypeFromSchema(
              shape: InputObjectTypeDefinition,
              _,
              _,
              _,
              _
            ),
            JsObject(fields)
            )
            if fieldsRespectsShape(fields, shape.fields.map(Right(_)).toList) =>
          json
        case (
            TypeFromSchema(
              enumDef: EnumTypeDefinition,
              _,
              _,
              _,
              _
            ),
            JsString(value)
            ) if enumDef.values.map(_.name).contains(value) =>
          json
        case _ => throw new QueryError("Type checking error")
      }
    }
}
