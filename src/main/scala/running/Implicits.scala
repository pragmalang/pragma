package running
import sangria.ast.{Document, Definition}
import spray.json._
import spray.json.DefaultJsonProtocol._
import running.pipeline._

import Implicits._
import sangria.ast._

package object Implicits {
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

  implicit object GraphQlObjectFieldJsonFormater
      extends JsonFormat[ObjectField] {
    override def read(json: JsValue): ObjectField =
      ObjectField(
        name = json.asJsObject.fields("name").convertTo[String],
        value = GraphQlValueJsonFormater.read(json.asJsObject.fields("value"))
      )
    override def write(obj: ObjectField): JsValue =
      JsObject(
        "name" -> obj.name.toJson,
        "value" -> GraphQlValueJsonFormater.write(obj.value),
        "kind" -> "ObjectField".toJson
      )
  }

  implicit object GraphQlValueJsonFormater extends JsonFormat[Value] {
    override def read(json: JsValue): Value = {
      val fields = json.asJsObject.fields
      val kind = fields("kind").convertTo[String]
      kind match {
        case "ListValue" =>
          ListValue(fields("values").convertTo[Vector[Value]])
        case "ObjectValue" =>
          ObjectValue(
            fields("fields").convertTo[Vector[ObjectField]]
          )
        case "BigDecimalValue" =>
          BigDecimalValue(fields("value").convertTo[BigDecimal])
        case "BigIntValue"   => BigIntValue(fields("value").convertTo[BigInt])
        case "IntValue"      => IntValue(fields("value").convertTo[Int])
        case "BooleanValue"  => BooleanValue(fields("value").convertTo[Boolean])
        case "FloatValue"    => FloatValue(fields("value").convertTo[Float])
        case "StringValue"   => StringValue(fields("value").convertTo[String])
        case "EnumValue"     => EnumValue(fields("value").convertTo[String])
        case "VariableValue" => VariableValue(fields("name").convertTo[String])
        case "NullValue"     => NullValue()
        case _ =>
          throw DeserializationException("Invalid GraphQl `Value` object")
      }
    }
    override def write(obj: Value): JsValue = obj match {
      case ListValue(values, comments, location) =>
        JsObject(
          "values" -> values.map(_.toJson).toJson,
          "kind" -> "ListValue".toJson
        )
      case ObjectValue(fields, comments, location) =>
        JsObject(
          "fields" -> fields.map(_.toJson).toJson,
          "kind" -> "ObjectValue".toJson
        )
      case BigDecimalValue(value, comments, location) =>
        JsObject(
          "value" -> value.toJson,
          "kind" -> "BigDecimalValue".toJson
        )
      case BigIntValue(value, comments, location) =>
        JsObject(
          "value" -> value.toJson,
          "kind" -> "BigIntValue".toJson
        )
      case IntValue(value, comments, location) =>
        JsObject(
          "value" -> value.toJson,
          "kind" -> "IntValue".toJson
        )
      case BooleanValue(value, comments, location) =>
        JsObject(
          "value" -> value.toJson,
          "kind" -> "BooleanValue".toJson
        )
      case FloatValue(value, comments, location) =>
        JsObject(
          "value" -> value.toJson,
          "kind" -> "FloatValue".toJson
        )
      case StringValue(value, block, blockRawValue, comments, location) =>
        JsObject(
          "value" -> value.toJson,
          "kind" -> "StringValue".toJson
        )
      case EnumValue(value, comments, location) =>
        JsObject(
          "value" -> value.toJson,
          "kind" -> "EnumValue".toJson
        )
      case VariableValue(name, comments, location) =>
        JsObject(
          "value" -> name.toJson,
          "kind" -> "VariableValue".toJson
        )
      case NullValue(comments, location) =>
        JsObject(
          "value" -> JsNull,
          "kind" -> "NullValue".toJson
        )
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

  implicit object ContextJsonFormater extends JsonFormat[Context] {
    def read(json: JsValue): Context = Context(
      data = json.asJsObject.fields.get("data"),
      user = json.asJsObject.fields("user").convertTo[Option[JwtPaylod]]
    )
    def write(obj: Context): JsValue = JsObject(
      "data" -> obj.data.toJson,
      "user" -> obj.user.toJson,
      "kind" -> "Context".toJson
    )
  }

  implicit object RequestJsonFormater extends JsonFormat[Request] {
    def read(json: JsValue): Request = Request(
      ctx = json.asJsObject.fields("ctx").convertTo[Context],
      graphQlQuery = json.asJsObject.fields("graphQlQuery").convertTo[Document],
      body = json.asJsObject.fields.get("body").map(_.asJsObject),
      cookies = json.asJsObject.fields("cookies").convertTo[Map[String, String]],
      url = json.asJsObject.fields("url").convertTo[String],
      hostname = json.asJsObject.fields("hostname").convertTo[String]
    )
    def write(obj: Request): JsValue = JsObject(
      "ctx" -> obj.ctx.toJson,
      "graphQlQuery" -> obj.graphQlQuery.toJson,
      "body" -> obj.body.toJson,
      "cookies" -> obj.cookies.toJson,
      "url" -> obj.url.toJson,
      "hostname" -> obj.hostname.toJson,
      "kind" -> "Request".toJson
    )
  }
}
