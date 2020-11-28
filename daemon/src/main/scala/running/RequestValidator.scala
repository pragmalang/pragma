package running

import scala.util.Try
import pragma.domain.SyntaxTree
import sangria.validation.QueryValidator
import sangria.schema.Schema
import setup.schemaGenerator.ApiSchemaGenerator
import sangria._
import spray.json._
import running.utils._
import sangria.schema.DefaultIntrospectionSchemaBuilder.MaterializedSchemaViolation

class RequestValidator(syntaxTree: SyntaxTree) {

  def apply(input: Request): Try[Request] =
    validateQuery(input)

  val queryValidator = QueryValidator.default

  val apiSchemaGenerator = ApiSchemaGenerator(syntaxTree)

  val apiSchemaAst = apiSchemaGenerator.build

  val sangriaSchema = Schema.buildFromAst(apiSchemaAst)

  def validateQuery(request: Request): Try[Request] = Try {
    val violations = queryValidator
      .validateQuery(sangriaSchema, request.query)

    if (!violations.isEmpty)
      throw new QueryError(violations.map(_.errorMessage))

    request
  }

  // 
  def typeCheckVariables(
      query: ast.Document,
      variables: JsObject
  ): Option[QueryError] = {

    def typeCheck(
        json: JsValue,
        tpe: schema.InputType[_]
    ): Option[QueryError] = {
      (json, tpe) match {
        case (_, t: schema.ScalarType[_]) => {
          t.coerceInput(utils.jsonToSangria(json)) match {
            case Left(MaterializedSchemaViolation) if t.name == "Any" => None
            case Left(_)                                              => Some(VariableCoercionError)
            case Right(_)                                             => None
          }
        }
        case (JsString(v), t: schema.EnumType[_]) => {
          val values = t.values.map(_.name)
          if (values.contains(v))
            None
          else
            Some(VariableCoercionError)
        }
        case (obj: JsObject, t: schema.InputObjectType[_]) => {
          val checkedFields =
            t.fieldsFn().map(f => typeCheck(obj.fields(f.name), f.fieldType))
          val errors = checkedFields.filter(_.isDefined)
          if (errors.isEmpty)
            None
          else
            Some(VariableCoercionError)
        }
        case (JsNull, _: schema.OptionInputType[_]) => None
        case (_, t: schema.OptionInputType[_])      => typeCheck(json, t.ofType)
        case (JsArray(elements), t: schema.ListInputType[_]) => {
          val errors =
            elements.map(e => typeCheck(e, t.ofType)).filter(_.isDefined)

          if (errors.isEmpty)
            None
          else
            Some(VariableCoercionError)
        }
        case _ => Some(VariableCoercionError)
      }

    }

    val vars = query.operations
      .flatMap(_._2.variables)
      .map(v => v -> variables.fields.get(v.name))

    print(vars)
    print(typeCheck _)
    ???
  }
}
