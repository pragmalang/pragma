package setup.schemaGenerator
import setup.utils._
import domain._, primitives._, Implicits._

import sangria.ast._
import sangria.macros._

case class DefaultApiSchemaGenerator(override val syntaxTree: SyntaxTree)
    extends GraphQlConverter(syntaxTree)
    with ApiSchemaGenerator {
  import ApiSchemaGenerator._

  def graphQlFieldArgs(args: Map[String, Type]) =
    args.map(arg => InputValueDefinition(arg._1, arg._2, None)).toVector

  def graphQlField(
      nameTransformer: String => String = identity,
      args: Map[String, Type],
      fieldType: Type
  )(modelId: String) = FieldDefinition(
    name = nameTransformer(modelId),
    fieldType = fieldType,
    arguments = graphQlFieldArgs(args)
  )

  def listFieldType(
      ht: HType,
      isOptional: Boolean = false,
      nameTransformer: String => String = identity
  ): Type = isOptional match {
    case true => ListType(fieldType(ht, isOptional = true, nameTransformer))
    case false =>
      NotNullType(ListType(fieldType(ht, isOptional = true, nameTransformer)))
  }

  def outputTypes: List[Definition] = typeDefinitions map {
    case objDef: ObjectTypeDefinition =>
      objDef.copy(fields = objDef.fields map { field =>
        field.fieldType match {
          case ListType(_, _) =>
            field.copy(
              arguments = graphQlFieldArgs(
                Map("where" -> builtinType(WhereInput, isOptional = true))
              )
            )
          case NotNullType(ListType(_, _), _) =>
            field.copy(
              arguments = graphQlFieldArgs(
                Map("where" -> builtinType(WhereInput, isOptional = true))
              )
            )
          case _ => field
        }
      })
    case td => td
  }

  def inputFieldType(field: HModelField)(kind: InputKind) = {
    val hReferenceType = fieldType(
      ht = field.htype,
      nameTransformer = inputTypeName(_)(kind match {
        case OptionalInput => OptionalInput
        case _             => ReferenceInput
      }),
      isOptional = kind match {
        case ObjectInput   => false
        case OptionalInput => true
        case ReferenceInput =>
          !field.directives.exists(fd => fd.id == "primary")
      }
    )

    val isReferenceToModel = (t: HReferenceType) =>
      syntaxTree.models.exists(_.id == t.id)

    field.htype match {
      case t: HReferenceType if isReferenceToModel(t) =>
        hReferenceType
      case HArray(t: HReferenceType) if isReferenceToModel(t) =>
        hReferenceType
      case HOption(t: HReferenceType) if isReferenceToModel(t) =>
        hReferenceType
      case _ =>
        fieldType(
          ht = field.htype,
          isOptional = kind match {
            case ObjectInput   => false
            case OptionalInput => true
            case ReferenceInput =>
              !field.directives.exists(fd => fd.id == "primary")
          }
        )
    }
  }

  def inputTypes(kind: InputKind): List[Definition] =
    syntaxTree.models.map { model =>
      InputObjectTypeDefinition(
        name = inputTypeName(model)(kind),
        fields = model.fields.toVector.map(
          field =>
            InputValueDefinition(
              name = field.id,
              valueType = inputFieldType(field)(kind),
              None
            )
        )
      )
    }

  def notificationTypes: List[Definition] =
    syntaxTree.models.map { model =>
      ObjectTypeDefinition(
        name = notificationTypeName(model),
        interfaces = Vector.empty,
        fields = Vector(
          FieldDefinition(
            name = "event",
            fieldType = builtinType(MultiRecordEvent),
            arguments = Vector.empty
          ),
          FieldDefinition(
            name = model.id.small,
            fieldType = fieldType(model),
            arguments = Vector.empty
          )
        )
      )
    }

  def ruleBasedTypeGenerator(
      typeName: String,
      rules: List[HModel => Option[FieldDefinition]]
  ) = ObjectTypeDefinition(
    typeName,
    Vector.empty,
    rules
      .foldLeft(List.empty[Option[FieldDefinition]])(
        (acc, rule) => acc ::: syntaxTree.models.map(rule)
      )
      .filter({
        case Some(field) => true
        case None        => false
      })
      .map(_.get)
      .toVector
  )

  def queryType: ObjectTypeDefinition = {
    import domain.utils._
    val rules: List[HModel => Option[FieldDefinition]] = List(
      model =>
        Some(
          graphQlField(
            nameTransformer = _.small,
            args = Map(
              model.primaryField.id -> fieldType(model.primaryField.htype)
            ),
            fieldType = outputType(model)
          )(model.id)
        ),
      model =>
        Some(
          graphQlField(
            _ => Pluralizer.pluralize(model).small,
            args = Map("where" -> builtinType(WhereInput, isOptional = true)),
            fieldType = outputType(model, isList = true)
          )(model.id)
        ),
      model =>
        Some(
          graphQlField(
            _ => "count" + Pluralizer.pluralize(model).capitalize,
            args = Map("where" -> builtinType(WhereInput, isOptional = true)),
            fieldType = builtinType(IntScalar)
          )(model.id)
        ),
      model =>
        Some(
          graphQlField(
            _ => model.id.small + "Exists",
            args = Map("filter" -> builtinType(LogicalFilterInput)),
            fieldType = builtinType(IntScalar)
          )(model.id)
        )
    )

    ruleBasedTypeGenerator("Query", rules)
  }

  def subscriptionType: ObjectTypeDefinition = {
    val rules: List[HModel => Option[FieldDefinition]] = List(
      model =>
        Some(
          graphQlField(
            nameTransformer = _.small,
            args = Map(
              model.primaryField.id -> fieldType(
                model.primaryField.htype,
                isOptional = true
              ),
              "on" -> builtinType(
                SingleRecordEvent,
                isList = true,
                isOptional = true
              )
            ),
            fieldType = outputType(
              model,
              nameTransformer = _ => notificationTypeName(model)
            )
          )(model.id)
        ),
      model =>
        Some(
          graphQlField(
            _ => Pluralizer.pluralize(model).small,
            args = Map(
              "where" -> builtinType(WhereInput, isOptional = true),
              "on" -> builtinType(
                MultiRecordEvent,
                isList = true,
                isOptional = true
              )
            ),
            fieldType = outputType(
              model,
              isList = true,
              nameTransformer = _ => notificationTypeName(model)
            )
          )(model.id)
        )
    )
    ruleBasedTypeGenerator("Subscription", rules)
  }

  def mutationType: ObjectTypeDefinition = {
    val rules: List[HModel => Option[FieldDefinition]] = List(
      model =>
        model.isUser match {
          case true =>
            Some(
              graphQlField(
                modelId => "login" + modelId.capitalize,
                args = Map(
                  "publicCredential" -> builtinType(
                    StringScalar,
                    isOptional = true
                  ),
                  "secretCredential" -> builtinType(
                    StringScalar,
                    isOptional = true
                  )
                ),
                fieldType = builtinType(StringScalar)
              )(model.id)
            )
          case false => None
        },
      model =>
        Some(
          graphQlField(
            modelId => "create" + modelId.capitalize,
            args = Map(
              model.id.small -> fieldType(
                model,
                nameTransformer = inputTypeName(_)(ObjectInput)
              )
            ),
            fieldType = outputType(model)
          )(model.id)
        ),
      model =>
        Some(
          graphQlField(
            modelId => "update" + modelId.capitalize,
            args = Map(
              model.primaryField.id -> fieldType(model.primaryField.htype),
              model.id.small -> fieldType(
                model,
                nameTransformer = inputTypeName(_)(OptionalInput)
              )
            ),
            fieldType = outputType(model)
          )(model.id)
        ),
      model =>
        Some(
          graphQlField(
            modelId => "upsert" + modelId.capitalize,
            args = Map(
              model.id.small -> fieldType(
                model,
                nameTransformer = inputTypeName(_)(OptionalInput)
              )
            ),
            fieldType = outputType(model)
          )(model.id)
        ),
      model =>
        Some(
          graphQlField(
            modelId => "delete" + modelId.capitalize,
            args =
              Map(model.primaryField.id -> fieldType(model.primaryField.htype)),
            fieldType = outputType(model)
          )(model.id)
        ),
      model =>
        Some(
          graphQlField(
            nameTransformer =
              _ => "create" + Pluralizer.pluralize(model).capitalize,
            args = Map(
              Pluralizer.pluralize(model).small -> listFieldType(
                model,
                nameTransformer = inputTypeName(_)(ObjectInput)
              )
            ),
            fieldType = outputType(model, isList = true)
          )(model.id)
        ),
      model =>
        Some(
          graphQlField(
            nameTransformer =
              _ => "update" + Pluralizer.pluralize(model).capitalize,
            args = Map(
              Pluralizer.pluralize(model).small -> listFieldType(
                model,
                nameTransformer = inputTypeName(_)(ReferenceInput)
              )
            ),
            fieldType = outputType(model, isList = true)
          )(model.id)
        ),
      model =>
        Some(
          graphQlField(
            nameTransformer =
              _ => "upsert" + Pluralizer.pluralize(model).capitalize,
            args = Map(
              Pluralizer.pluralize(model).small -> listFieldType(
                model,
                nameTransformer = inputTypeName(_)(OptionalInput)
              )
            ),
            fieldType = outputType(model, isList = true)
          )(model.id)
        ),
      model =>
        Some(
          graphQlField(
            _ => "delete" + Pluralizer.pluralize(model).capitalize,
            args = Map(
              model.primaryField.id -> listFieldType(model.primaryField.htype)
            ),
            fieldType = outputType(model, isList = true)
          )(model.id)
        )
    )

    ruleBasedTypeGenerator("Mutation", rules)
  }

  def gqlFieldToHModelField(
      field: Either[FieldDefinition, InputValueDefinition]
  ): HModelField = {
    val apiSchema = buildApiSchemaAsDocument
    def fieldType(
        tpe: Type,
        fieldHType: HType,
        isOptional: Boolean = true
    ): HType =
      tpe match {
        case ListType(ofType, _) =>
          HArray(fieldType(ofType, fieldHType))
        case NamedType(name, _) =>
          if (isOptional) HOption(fieldHType) else fieldHType
        case NotNullType(ofType, _) => fieldType(ofType, fieldHType, false)
      }

    field match {
      case Left(field) => {
        val fieldHType = gqlTypeToHType(
          getTypeFromSchema(field.fieldType.namedType).get.typeDef
        )
        HModelField(
          id = field.name,
          htype = fieldType(field.fieldType, fieldHType),
          None,
          Nil,
          None
        )
      }
      case Right(value) => {
        val fieldHType = gqlTypeToHType(
          getTypeFromSchema(value.valueType.namedType).get.typeDef
        )
        HModelField(
          id = value.name,
          htype = fieldType(value.valueType, fieldHType),
          None,
          Nil,
          None
        )
      }
    }
  }

  def gqlTypeToHType(
      typeDef: TypeDefinition,
      isReference: Boolean = true
  ): HType = typeDef match {
    case i: InterfaceTypeDefinition if isReference => HReference(i.name)
    case i: InterfaceTypeDefinition if !isReference =>
      HModel(
        id = i.name,
        fields = i.fields.map(f => gqlFieldToHModelField(Left(f))).toList,
        Nil,
        None
      )
    case e: EnumTypeDefinition =>
      HEnum(id = e.name, values = e.values.map(v => v.name).toList, None)
    case s: ScalarTypeDefinition if s.name == "Int"     => HInteger
    case s: ScalarTypeDefinition if s.name == "String"  => HString
    case s: ScalarTypeDefinition if s.name == "Float"   => HFloat
    case s: ScalarTypeDefinition if s.name == "Boolean" => HBool
    case s: ScalarTypeDefinition if s.name == "ID"      => HString
    case s: ScalarTypeDefinition if s.name == "Any"     => HAny
    case _: UnionTypeDefinition =>
      throw new Exception(
        "GraphQL unions types can't be converted to Heavenly-x types"
      )
    case o: ObjectTypeDefinition if isReference => HReference(o.name)
    case o: ObjectTypeDefinition if !isReference =>
      HModel(
        id = o.name,
        fields = o.fields.map(f => gqlFieldToHModelField(Left(f))).toList,
        Nil,
        None
      )
    case i: InputObjectTypeDefinition if isReference => HReference(i.name)
    case i: InputObjectTypeDefinition if !isReference =>
      HModel(
        id = i.name,
        fields = i.fields.map(f => gqlFieldToHModelField(Right(f))).toList,
        Nil,
        None
      )
  }

  def build(as: SchemaBuildOutput = AsSyntaxTree): Either[SyntaxTree, Document] = as match {
    case AsSyntaxTree => Left(buildApiSchemaAsSyntaxTree)
    case AsDocument => Right(buildApiSchemaAsDocument)
  }

  lazy val buildApiSchemaAsSyntaxTree: SyntaxTree = {
    val apiSchema = buildApiSchemaAsDocument
    val definitions = apiSchema.definitions
      .filter {
        case d: ObjectTypeDefinition
            if d.name == "Query" || d.name == "Mutation" || d.name == "Subscription" =>
          false
        case d: TypeDefinition if d.name == "Any" => false
        case _: TypeDefinition                    => true
        case _                                    => false
      }
      .map(_.asInstanceOf[TypeDefinition])
      .map(t => gqlTypeToHType(t, false))
      .collect { case t: HConstruct => t }
      .toList
    SyntaxTree.fromConstructs(definitions)
  }

  lazy val buildApiSchemaAsDocument = Document {
    (queryType
      :: mutationType
      :: subscriptionType
      :: buitlinGraphQlTypeDefinitions
      ::: outputTypes
      ::: inputTypes(ObjectInput)
      ::: inputTypes(ReferenceInput)
      ::: inputTypes(OptionalInput)
      ::: notificationTypes).toVector
  }

  def getTypeFromSchema(tpe: Type): Option[TypeFromSchema] = Option {
    val td = buildApiSchemaAsDocument.definitions
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
}
