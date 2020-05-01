package running

import sangria.ast.{Document, Definition}
import spray.json._
import spray.json.DefaultJsonProtocol._
import running.pipeline._
import sangria.ast._

package object Implicits {
  import setup.storage.ModelQueryPredicate
  import setup.storage.ArrayQueryPredicate
  import setup.storage.NumberQueryPredicate
  import setup.storage.StringQueryPredicate
  import domain.primitives._

  import setup.storage._
  import sangria.ast.OperationType._

  implicit object GraphQlOperationTypeJsonFormater
      extends JsonFormat[OperationType] {
    override def read(json: JsValue): OperationType =
      json.asInstanceOf[JsString].value match {
        case "Mutation"     => Mutation
        case "Query"        => Query
        case "Subscription" => Subscription
      }
    override def write(obj: OperationType): JsValue = obj match {
      case Mutation     => "Mutation".toJson
      case Query        => "Query".toJson
      case Subscription => "Subscription".toJson
    }
  }

  implicit object GraphQlTypeFormater extends JsonFormat[Type] {
    override def read(json: JsValue): Type = json match {
      case JsObject(fields) if fields("kind").convertTo[String] == "ListType" =>
        ListType(fields("type").convertTo[Type])
      case JsObject(fields)
          if fields("kind").convertTo[String] == "NotNullType" =>
        NotNullType(fields("type").convertTo[Type])
      case JsObject(fields)
          if fields("kind").convertTo[String] == "NamedType" =>
        NamedType(fields("name").convertTo[String])
    }
    override def write(obj: Type): JsValue = obj match {
      case ListType(ofType, location) =>
        JsObject(
          "kind" -> "ListType".toJson,
          "type" -> ofType.toJson
        )
      case NamedType(name, location) =>
        JsObject("name" -> name.toJson, "kind" -> "NamedType".toJson)
      case NotNullType(ofType, location) =>
        JsObject(
          "kind" -> "NotNullType".toJson,
          "type" -> ofType.toJson
        )
    }
  }
  implicit object GraphQlFieldDefinitionFormater
      extends JsonFormat[FieldDefinition] {
    override def read(json: JsValue): FieldDefinition =
      FieldDefinition(
        json.asJsObject.fields("name").convertTo[String],
        json.asJsObject.fields("fieldType").convertTo[Type],
        Vector.empty
      )
    override def write(obj: FieldDefinition): JsValue =
      JsObject(
        "name" -> obj.name.toJson,
        "fieldType" -> obj.fieldType.toJson,
        "kind" -> "FieldDefinition".toJson
      )
  }

  implicit object QueryWhereJsonFormater extends JsonFormat[QueryWhere] {
    override def read(json: JsValue): QueryWhere = ???
    override def write(obj: QueryWhere): JsValue = ???
  }

  implicit object QueryFilterJsonFormater extends JsonFormat[QueryFilter] {
    override def read(json: JsValue): QueryFilter = ???
    override def write(obj: QueryFilter): JsValue = ???
  }

  implicit object QueryPredicateJsonFormater
      extends JsonFormat[QueryPredicate[_]] {
    override def read(json: JsValue): QueryPredicate[_] = ???
    override def write(obj: QueryPredicate[_]): JsValue = obj match {
      case ModelQueryPredicate(_, fieldPredicates) =>
        JsObject(
          fieldPredicates
            .map(f => f._1 -> QueryPredicateJsonFormater.write(f._2))
        )
      case ArrayQueryPredicate(length) => JsObject("length" -> QueryPredicateJsonFormater.write(length))
      case NumberQueryPredicate(lt, lte, eq, gt, gte) =>
        JsObject(
          "lt" -> lt.toJson,
          "lte" -> lte.toJson,
          "eq" -> eq.toJson,
          "gt" -> gt.toJson,
          "gte" -> gte.toJson
        )
      case StringQueryPredicate(length, startsWith, endsWith, matches) => 
        JsObject(
          "length" -> length.toJson,
          "startsWith" -> startsWith.toJson,
          "endsWith" -> endsWith.toJson,
          "matches" -> matches.toJson
        )
      case EnumQueryPredicate(value) => value.toJson
      case QueryFilter(predicate, and, or, negate) => JsObject(
        "predicate" -> QueryPredicateJsonFormater.write(predicate),
        "and" -> and.map(_.toJson).toJson,
        "or" -> or.map(_.toJson).toJson,
        "not" -> negate.toJson
      )
    }
  }

  implicit object PValueJsonWriter extends JsonWriter[PValue] {
    override def write(obj: PValue): JsValue = obj match {
      case PStringValue(value) => JsString(value)
      case PIntValue(value)    => JsNumber(value)
      case PFloatValue(value)  => JsNumber(value)
      case PBoolValue(value)   => JsBoolean(value)
      case PDateValue(value)   => JsString(value.toString())
      case PArrayValue(values, elementType) =>
        JsArray(values.map(PValueJsonWriter.write(_)).toVector)
      case PFileValue(value, ptype) => JsString(value.toString())
      case PModelValue(value, ptype) =>
        JsObject(value.map {
          case (key, value) => (key, PValueJsonWriter.write(value))
        })
      case PInterfaceValue(value, ptype) =>
        JsObject(value.map {
          case (key, value) => (key, PValueJsonWriter.write(value))
        })
      case _: PFunctionValue[_, _] =>
        throw new SerializationException(
          "Pragma functions are not serializable"
        )
      case POptionValue(value, valueType) =>
        value.map(PValueJsonWriter.write(_)).getOrElse(JsNull)
    }
  }

  implicit object GraphQlValueJsonFormater extends JsonFormat[Value] {
    override def read(json: JsValue): Value = json match {
      case JsObject(fields) =>
        ObjectValue(
          fields
            .map(field => ObjectField(field._1, field._2.convertTo[Value]))
            .toVector
        )
      case JsArray(elements)                 => ListValue(elements.map(_.convertTo[Value]))
      case JsString(value)                   => StringValue(value)
      case JsNumber(value) if value.isWhole  => BigIntValue(value.toBigInt)
      case JsNumber(value) if !value.isWhole => BigDecimalValue(value)
      case JsTrue                            => BooleanValue(true)
      case JsFalse                           => BooleanValue(false)
      case JsNull                            => NullValue()
    }
    override def write(obj: Value): JsValue = obj match {
      case ListValue(values, comments, location) =>
        JsArray(values.map(_.toJson).toJson)
      case ObjectValue(fields, comments, location) =>
        JsObject(fields.map(field => field.name -> field.value.toJson).toMap)
      case BigDecimalValue(value, comments, location) => value.toJson
      case BigIntValue(value, comments, location)     => value.toJson
      case IntValue(value, comments, location)        => value.toJson
      case FloatValue(value, comments, location)      => value.toJson
      case BooleanValue(value, comments, location)    => value.toJson
      case StringValue(value, block, blockRawValue, comments, location) =>
        value.toJson
      case EnumValue(value, comments, location) => value.toJson
      case VariableValue(name, comments, location) =>
        throw new InternalError(
          "GraphQL variable values cannot be serialized. They must be substituted first."
        )
      case NullValue(comments, location) => JsNull
    }
  }

  implicit object GraphQlArgumentJsonFormater extends JsonFormat[Argument] {
    override def read(json: JsValue): Argument =
      Argument(
        json.asJsObject.fields("name").convertTo[String],
        json.asJsObject.fields("value").convertTo[Value]
      )
    override def write(obj: Argument): JsValue = JsObject(
      "name" -> obj.name.toJson,
      "value" -> obj.value.toJson,
      "kind" -> "Argument".toJson
    )
  }

  implicit object GraphQlDirectiveJsonFormater extends JsonFormat[Directive] {
    override def read(json: JsValue): Directive =
      Directive(
        json.asJsObject.fields("name").convertTo[String],
        json.asJsObject.fields("arguments").convertTo[Vector[Argument]]
      )
    override def write(obj: Directive): JsValue = JsObject(
      "name" -> obj.name.toJson,
      "arguments" -> obj.arguments.toJson,
      "kind" -> "Directive".toJson
    )
  }

  implicit object GraphQlSelectionJsonFormater extends JsonFormat[Selection] {
    override def read(json: JsValue): Selection = {
      val fields = json.asJsObject.fields
      val kind = fields("kind").convertTo[String]
      kind match {
        case "Field" =>
          Field(
            alias = fields.get("alias").map(_.convertTo[String]),
            name = fields("name").convertTo[String],
            arguments = fields("arguments").convertTo[Vector[Argument]],
            directives = fields("directives").convertTo[Vector[Directive]],
            selections = fields("selections").convertTo[Vector[Selection]]
          )
        case "FragmentSpread" =>
          FragmentSpread(
            name = fields("name").convertTo[String],
            directives = fields("directives").convertTo[Vector[Directive]]
          )
        case "InlineFragment" =>
          InlineFragment(
            typeCondition = fields("typeCondition")
              .convertTo[Option[Type]]
              .asInstanceOf[Option[NamedType]],
            directives = fields("directives").convertTo[Vector[Directive]],
            selections = fields("selections").convertTo[Vector[Selection]]
          )
        case _ =>
          throw DeserializationException("Invalid GraphQl `Selection` object")
      }
    }
    override def write(obj: Selection): JsValue = obj match {
      case Field(alias, name, arguments, directives, selections, _, _, _) =>
        JsObject(
          "alias" -> alias.toJson,
          "name" -> name.toJson,
          "arguments" -> arguments.toJson,
          "directives" -> directives.toJson,
          "selections" -> selections.toJson,
          "kind" -> "Field".toJson
        )
      case FragmentSpread(name, directives, _, _) =>
        JsObject(
          "name" -> name.toJson,
          "directives" -> directives.toJson,
          "kind" -> "FragmentSpread".toJson
        )
      case InlineFragment(typeCondition, directives, selections, _, _, _) =>
        JsObject(
          "typeCondition" -> typeCondition.asInstanceOf[Option[Type]].toJson,
          "directives" -> directives.toJson,
          "selections" -> selections.toJson,
          "kind" -> "InlineFragment".toJson
        )
    }
  }

  implicit object GraphQlVariableDefinitionFormater
      extends JsonFormat[VariableDefinition] {
    override def read(json: JsValue): VariableDefinition =
      VariableDefinition(
        json.asJsObject.fields("name").convertTo[String],
        json.asJsObject.fields("fieldType").convertTo[Type],
        json.asJsObject.fields.get("defaultValue").map(_.convertTo[Value])
      )
    override def write(obj: VariableDefinition): JsValue =
      JsObject(
        "name" -> obj.name.toJson,
        "fieldType" -> obj.tpe.toJson,
        "defaultValue" -> obj.defaultValue.toJson,
        "kind" -> "VariableDefinition".toJson
      )
  }

  implicit object GraphQlInputValueDefinitionJsonFormater
      extends JsonFormat[InputValueDefinition] {
    override def read(json: JsValue): InputValueDefinition =
      InputValueDefinition(
        name = json.asJsObject.fields("name").convertTo[String],
        valueType = json.asJsObject.fields("valueType").convertTo[Type],
        defaultValue =
          json.asJsObject.fields("defaultValue").convertTo[Option[Value]],
        directives =
          json.asJsObject.fields("directives").convertTo[Vector[Directive]],
        description = json.asJsObject
          .fields("description")
          .convertTo[Option[Value]]
          .asInstanceOf[Option[StringValue]]
      )
    override def write(obj: InputValueDefinition): JsValue = JsObject(
      "name" -> obj.name.toJson,
      "valueType" -> obj.valueType.toJson,
      "defaultValue" -> obj.defaultValue.toJson,
      "directives" -> obj.directives.toJson,
      "description" -> obj.description.map(_.asInstanceOf[Value].toJson).toJson,
      "kind" -> "InputValueDefinition".toJson
    )
  }

  implicit object GraphQlEnumValueDefinitionJsonFormater
      extends JsonFormat[EnumValueDefinition] {
    override def read(json: JsValue): EnumValueDefinition = EnumValueDefinition(
      name = json.asJsObject.fields("name").convertTo[String],
      directives =
        json.asJsObject.fields("directives").convertTo[Vector[Directive]],
      description = json.asJsObject
        .fields("description")
        .convertTo[Option[Value]]
        .asInstanceOf[Option[StringValue]]
    )
    override def write(obj: EnumValueDefinition): JsValue = JsObject(
      "name" -> obj.name.toJson,
      "directives" -> obj.directives.toJson,
      "description" -> obj.description.map(_.asInstanceOf[Value].toJson).toJson,
      "kind" -> "EnumValueDefinition".toJson
    )
  }

  implicit object GraphQlOperationTypeDefinitionJsonFormater
      extends JsonFormat[OperationTypeDefinition] {
    override def read(json: JsValue): OperationTypeDefinition =
      OperationTypeDefinition(
        operation = json.asJsObject.fields("operation").convertTo[OperationType],
        tpe =
          json.asJsObject.fields("tpe").convertTo[Type].asInstanceOf[NamedType]
      )
    override def write(obj: OperationTypeDefinition): JsValue = JsObject(
      "operation" -> obj.operation.toJson,
      "tpe" -> obj.tpe.asInstanceOf[Type].toJson,
      "kind" -> "OperationTypeDefinition".toJson
    )
  }

  implicit object GraphQlDefinitionJsonFormater extends JsonFormat[Definition] {
    def read(json: JsValue): Definition = {
      val fields = json.asJsObject.fields
      val kind = fields("kind").convertTo[String]
      kind match {
        case "ObjectTypeDefinition" =>
          ObjectTypeDefinition(
            name = fields("name").convertTo[String],
            fields = fields("fields").convertTo[Vector[FieldDefinition]],
            directives = fields("directive").convertTo[Vector[Directive]],
            description = fields("description")
              .convertTo[Option[Value]]
              .asInstanceOf[Option[StringValue]],
            interfaces = fields("interfaces")
              .convertTo[Vector[Type]]
              .asInstanceOf[Vector[NamedType]]
          )
        case "EnumTypeDefinition" =>
          EnumTypeDefinition(
            name = fields("name").convertTo[String],
            values = fields("values").convertTo[Vector[EnumValueDefinition]],
            directives = fields("directives").convertTo[Vector[Directive]]
          )
        case "OperationDefinition" =>
          OperationDefinition(
            operationType = fields("operationType").convertTo[OperationType],
            name = fields.get("name").map(_.convertTo[String]),
            variables =
              fields("variables").convertTo[Vector[VariableDefinition]],
            directives = fields("directives").convertTo[Vector[Directive]],
            selections = fields("selections").convertTo[Vector[Selection]]
          )
        case "FragmentDefinition" =>
          FragmentDefinition(
            typeCondition =
              fields("typeCondition").convertTo[Type].asInstanceOf[NamedType],
            name = fields("name").convertTo[String],
            variables =
              fields("variables").convertTo[Vector[VariableDefinition]],
            directives = fields("directives").convertTo[Vector[Directive]],
            selections = fields("selections").convertTo[Vector[Selection]]
          )
        case "DirectiveDefinition" =>
          DirectiveDefinition(
            name = fields("name").convertTo[String],
            arguments =
              fields("arguments").convertTo[Vector[InputValueDefinition]],
            locations = Vector.empty,
            description = fields
              .get("description")
              .map(_.convertTo[Value].asInstanceOf[StringValue])
          )
        case "EnumTypeExtensionDefinition" =>
          EnumTypeExtensionDefinition(
            name = fields("name").convertTo[String],
            values = fields("values").convertTo[Vector[EnumValueDefinition]],
            directives = fields("directives").convertTo[Vector[Directive]]
          )
        case "InputObjectTypeDefinition" =>
          InputObjectTypeDefinition(
            name = fields("name").convertTo[String],
            fields = fields("fields").convertTo[Vector[InputValueDefinition]],
            directives = fields("directives").convertTo[Vector[Directive]],
            description = fields
              .get("description")
              .map(_.convertTo[Value].asInstanceOf[StringValue])
          )
        case "InputObjectTypeExtensionDefinition" =>
          InputObjectTypeExtensionDefinition(
            name = fields("name").convertTo[String],
            fields = fields("fields").convertTo[Vector[InputValueDefinition]],
            directives = fields("directives").convertTo[Vector[Directive]]
          )
        case "InterfaceTypeDefinition" =>
          InterfaceTypeDefinition(
            name = fields("name").convertTo[String],
            fields = fields("fields").convertTo[Vector[FieldDefinition]],
            directives = fields("directives").convertTo[Vector[Directive]],
            description = fields
              .get("description")
              .map(_.convertTo[Value].asInstanceOf[StringValue])
          )
        case "InterfaceTypeExtensionDefinition" =>
          InterfaceTypeExtensionDefinition(
            name = fields("name").convertTo[String],
            fields = fields("fields").convertTo[Vector[FieldDefinition]],
            directives = fields("directives").convertTo[Vector[Directive]]
          )
        case "ObjectTypeExtensionDefinition" =>
          ObjectTypeExtensionDefinition(
            name = fields("name").convertTo[String],
            fields = fields("fields").convertTo[Vector[FieldDefinition]],
            directives = fields("directives").convertTo[Vector[Directive]],
            interfaces = fields("interfaces")
              .convertTo[Vector[Type]]
              .asInstanceOf[Vector[NamedType]]
          )

        case "ScalarTypeDefinition" =>
          ScalarTypeDefinition(
            name = fields("name").convertTo[String],
            directives = fields("directives").convertTo[Vector[Directive]],
            description = fields
              .get("description")
              .map(_.convertTo[Value].asInstanceOf[StringValue])
          )
        case "ScalarTypeExtensionDefinition" =>
          ScalarTypeExtensionDefinition(
            name = fields("name").convertTo[String],
            directives = fields("directives").convertTo[Vector[Directive]]
          )
        case "SchemaDefinition" =>
          SchemaDefinition(
            operationTypes = fields("operationTypes")
              .convertTo[Vector[OperationTypeDefinition]],
            directives = fields("directives").convertTo[Vector[Directive]],
            description = fields
              .get("description")
              .map(_.convertTo[Value].asInstanceOf[StringValue])
          )
        case "SchemaExtensionDefinition" =>
          SchemaExtensionDefinition(
            operationTypes = fields("operationTypes")
              .convertTo[Vector[OperationTypeDefinition]],
            directives = fields("directives").convertTo[Vector[Directive]]
          )
        case "UnionTypeDefinition" =>
          UnionTypeDefinition(
            name = fields("name").convertTo[String],
            directives = fields("directives").convertTo[Vector[Directive]],
            description = fields
              .get("description")
              .map(_.convertTo[Value].asInstanceOf[StringValue]),
            types = fields("types")
              .convertTo[Vector[Type]]
              .asInstanceOf[Vector[NamedType]]
          )
        case "UnionTypeExtensionDefinition" =>
          UnionTypeExtensionDefinition(
            name = fields("name").convertTo[String],
            directives = fields("directives").convertTo[Vector[Directive]],
            types = fields("types")
              .convertTo[Vector[Type]]
              .asInstanceOf[Vector[NamedType]]
          )
        case _ =>
          throw DeserializationException("Invalid GraphQl `Definition` object")
      }
    }
    def write(obj: Definition): JsValue = obj match {
      case d: ObjectTypeDefinition =>
        JsObject(
          "name" -> d.name.toJson,
          "fields" -> d.fields.map(_.toJson).toJson,
          "interfaces" -> d.interfaces.map(_.asInstanceOf[Type]).toJson,
          "directives" -> d.directives.toJson,
          "description" -> d.description.asInstanceOf[Option[Value]].toJson,
          "kind" -> "ObjectTypeDefinition".toJson
        )
      case d: EnumTypeDefinition =>
        JsObject(
          "name" -> d.name.toJson,
          "values" -> d.values.toJson,
          "directives" -> d.directives.toJson,
          "kind" -> "EnumTypeDefinition".toJson
        )
      case d: OperationDefinition =>
        JsObject(
          "operationType" -> d.operationType.toJson,
          "name" -> d.name.toJson,
          "variables" -> d.variables.toJson,
          "directives" -> d.directives.toJson,
          "selections" -> d.selections.toJson,
          "kind" -> "OperationDefinition".toJson
        )
      case d: FragmentDefinition =>
        JsObject(
          "name" -> d.name.toJson,
          "typeCondition" -> d.typeCondition.asInstanceOf[Type].toJson,
          "directives" -> d.directives.toJson,
          "selections" -> d.selections.toJson,
          "variables" -> d.variables.toJson,
          "kind" -> "FragmentDefinition".toJson
        )
      case d: DirectiveDefinition =>
        JsObject(
          "name" -> d.name.toJson,
          "description" -> d.description
            .map(_.asInstanceOf[Value].toJson)
            .toJson,
          "arguments" -> d.arguments.toJson,
          "kind" -> "DirectiveDefinition".toJson
        )
      case d: EnumTypeExtensionDefinition =>
        JsObject(
          "name" -> d.name.toJson,
          "values" -> d.values.toJson,
          "directives" -> d.directives.toJson,
          "kind" -> "EnumTypeExtensionDefinition".toJson
        )
      case d: InputObjectTypeDefinition =>
        JsObject(
          "name" -> d.name.toJson,
          "fields" -> d.fields.toJson,
          "directives" -> d.directives.toJson,
          "description" -> d.description
            .map(_.asInstanceOf[Value].toJson)
            .toJson,
          "kind" -> "InputObjectTypeDefinition".toJson
        )
      case d: InputObjectTypeExtensionDefinition =>
        JsObject(
          "name" -> d.name.toJson,
          "fields" -> d.fields.toJson,
          "directives" -> d.directives.toJson,
          "kind" -> "InputObjectTypeExtensionDefinition".toJson
        )
      case d: InterfaceTypeDefinition =>
        JsObject(
          "name" -> d.name.toJson,
          "fields" -> d.fields.toJson,
          "directives" -> d.directives.toJson,
          "description" -> d.description
            .map(_.asInstanceOf[Value].toJson)
            .toJson,
          "kind" -> "InterfaceTypeDefinition".toJson
        )
      case d: InterfaceTypeExtensionDefinition =>
        JsObject(
          "name" -> d.name.toJson,
          "fields" -> d.fields.toJson,
          "directives" -> d.directives.toJson,
          "kind" -> "InterfaceTypeExtensionDefinition".toJson
        )
      case d: ObjectTypeExtensionDefinition =>
        JsObject(
          "name" -> d.name.toJson,
          "interfaces" -> d.interfaces.map(_.asInstanceOf[Type]).toJson,
          "fields" -> d.fields.toJson,
          "directives" -> d.directives.toJson,
          "kind" -> "ObjectTypeExtensionDefinition".toJson
        )
      case d: ScalarTypeDefinition =>
        JsObject(
          "name" -> d.name.toJson,
          "directives" -> d.directives.toJson,
          "description" -> d.description
            .map(_.asInstanceOf[Value].toJson)
            .toJson,
          "kind" -> "ScalarTypeDefinition".toJson
        )
      case d: ScalarTypeExtensionDefinition =>
        JsObject(
          "name" -> d.name.toJson,
          "directives" -> d.directives.toJson,
          "kind" -> "ScalarTypeExtensionDefinition".toJson
        )
      case d: SchemaDefinition =>
        JsObject(
          "operationTypes" -> d.operationTypes.toJson,
          "directives" -> d.directives.toJson,
          "description" -> d.description.map(_.asInstanceOf[Value]).toJson,
          "kind" -> "SchemaDefinition".toJson
        )
      case d: SchemaExtensionDefinition =>
        JsObject(
          "operationTypes" -> d.operationTypes.toJson,
          "directives" -> d.directives.toJson,
          "kind" -> "SchemaExtensionDefinition".toJson
        )
      case d: UnionTypeDefinition =>
        JsObject(
          "name" -> d.name.toJson,
          "types" -> d.types.map(_.asInstanceOf[Type]).toJson,
          "directives" -> d.directives.toJson,
          "description" -> d.description.map(_.asInstanceOf[Value]).toJson,
          "kind" -> "UnionTypeDefinition".toJson
        )
      case d: UnionTypeExtensionDefinition =>
        JsObject(
          "name" -> d.name.toJson,
          "types" -> d.types.map(_.asInstanceOf[Type]).toJson,
          "directives" -> d.directives.toJson,
          "kind" -> "UnionTypeExtensionDefinition".toJson
        )
    }
  }

  implicit object GraphQlDocumentJsonFormater extends JsonFormat[Document] {
    def read(json: JsValue): Document =
      Document(
        json.asJsObject.fields("definitions").convertTo[Vector[Definition]]
      )
    def write(obj: Document): JsValue =
      JsObject(
        "definitions" -> obj.definitions.map(_.toJson).toJson,
        "kind" -> "Document".toJson
      )
  }

  implicit object JwtPaylodJsonFormater extends JsonFormat[JwtPaylod] {
    def read(json: JsValue): JwtPaylod = JwtPaylod(
      json.asJsObject.fields("userId").convertTo[String],
      json.asJsObject.fields("role").convertTo[String]
    )

    def write(obj: JwtPaylod): JsValue =
      JsObject("userId" -> JsString(obj.userId), "role" -> JsString(obj.role))
  }

  implicit object RequestJsonFormater extends JsonFormat[Request] {
    def read(json: JsValue): Request = Request(
      hookData = Some(json.asJsObject.fields("hookData")),
      body = Some(json.asJsObject.fields("body").asJsObject),
      query = json.asJsObject.fields("query").convertTo[Document],
      queryVariables = json.asJsObject.fields("queryVariables") match {
        case obj: JsObject => Left(obj)
        case arr: JsArray  => Right(arr.convertTo[List[JsObject]])
        case _ =>
          throw DeserializationException(
            "Query variables must only be an Array or Object"
          )
      },
      cookies = json.asJsObject.fields("cookies").convertTo[Map[String, String]],
      url = json.asJsObject.fields("url").convertTo[String],
      hostname = json.asJsObject.fields("hostname").convertTo[String],
      user = json.asJsObject.fields("user").convertTo[Option[JwtPaylod]]
    )
    def write(obj: Request): JsValue = JsObject(
      "hookData" -> obj.hookData.toJson,
      "body" -> obj.body.toJson,
      "kind" -> "Request".toJson,
      "query" -> obj.query.toJson,
      "queryVariables" -> (obj.queryVariables match {
        case Left(vars)  => vars.toJson
        case Right(vars) => vars.toJson
      }),
      "cookies" -> obj.cookies.toJson,
      "url" -> obj.url.toJson,
      "hostname" -> obj.hostname.toJson,
      "user" -> obj.user.toJson,
      "kind" -> "Context".toJson
    )
  }
}

/*
{
  User {
    even: list(where: {}) {
      username
    }
    odd: list(where: {}) {
      username
    }
  }
}
 */
