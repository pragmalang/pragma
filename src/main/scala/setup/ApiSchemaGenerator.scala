package setup
import domain._, primitives._, utils._

import sangria.ast._
import sangria.macros._

trait ApiSchemaGenerator {
  def buildApiSchema: Document
}

case class DefaultApiSchemaGenerator(override val syntaxTree: SyntaxTree)
    extends GraphQlConverter(syntaxTree)
    with ApiSchemaGenerator {
  import DefualtApiSchemaGenerator._

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
    case false => NotNullType(ListType(fieldType(ht, isOptional = true, nameTransformer)))
  }

  def outputTypes: List[Definition] = typeDefinitions map {
    case objDef: ObjectTypeDefinition =>
      objDef.copy(fields = objDef.fields map { field =>
        field.fieldType match {
          case ListType(_, _) =>
            field.copy(
              arguments =
                graphQlFieldArgs(Map("where" -> builtinType(WhereInput, isOptional = true)))
            )
          case NotNullType(ListType(_, _), _) =>
            field.copy(
              arguments =
                graphQlFieldArgs(Map("where" -> builtinType(WhereInput, isOptional = true)))
            )
          case _ => field
        }
      })
    case td => td
  }

  def inputTypes(kind: InputKind): List[Definition] =
    syntaxTree.models.map { model =>
      InputObjectTypeDefinition(
        name = inputTypeName(model)(kind),
        fields = model.fields.toVector.map { field =>
          InputValueDefinition(
            name = field.id,
            valueType = fieldType(
              ht = field.htype,
              nameTransformer = inputTypeName(_)(ReferenceInput),
              isOptional = kind match {
                case ObjectInput   => false
                case OptionalInput => true
                case ReferenceInput =>
                  !field.directives.exists(fd => fd.id == "primary")
              }
            ),
            None
          )
        }
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
            name = model.id,
            fieldType = fieldType(model),
            arguments = Vector.empty
          )
        )
      )
    }

  def ruleBasedTypeGenerator(
      typeName: String,
      rules: List[HModel => FieldDefinition]
  ) = ObjectTypeDefinition(
    typeName,
    Vector.empty,
    rules
      .foldLeft[List[FieldDefinition]](Nil)(_ ::: syntaxTree.models.map(_))
      .toVector
  )

  def queryType: ObjectTypeDefinition = {
    val rules: List[HModel => FieldDefinition] = List(
      model =>
        graphQlField(
          nameTransformer = _.toLowerCase,
          args = Map(
            model.primaryField.id -> fieldType(
              model.primaryField.htype,
              isOptional = true
            )
          ),
          fieldType = outputType(model)
        )(model.id),
      model =>
        graphQlField(
          _ => Pluralizer.pluralize(model).toLowerCase,
          args = Map("where" -> builtinType(WhereInput)),
          fieldType = outputType(model, isList = true)
        )(model.id),
      model =>
        graphQlField(
          _ => "count" + Pluralizer.pluralize(model).capitalize,
          args = Map("where" -> builtinType(WhereInput)),
          fieldType = builtinType(GqlInt)
        )(model.id),
      model =>
        graphQlField(
          _ => model.id.toLowerCase + "Exists",
          args = Map("filter" -> builtinType(LogicalFilterInput)),
          fieldType = builtinType(GqlInt)
        )(model.id)
    )

    ruleBasedTypeGenerator("Query", rules)
  }

  def subscriptionType: ObjectTypeDefinition = {
    val rules: List[HModel => FieldDefinition] = List(
      model =>
        graphQlField(
          nameTransformer = _.toLowerCase,
          args = Map(
            model.primaryField.id -> fieldType(
              model.primaryField.htype,
              isOptional = true
            ),
            "on" -> builtinType(SingleRecordEvent, isList = true)
          ),
          fieldType = outputType(
            model,
            nameTransformer = _ => notificationTypeName(model)
          )
        )(model.id),
      model =>
        graphQlField(
          _ => Pluralizer.pluralize(model).toLowerCase,
          args = Map(
            "where" -> builtinType(WhereInput),
            "on" -> builtinType(MultiRecordEvent, isList = true)
          ),
          fieldType = outputType(
            model,
            isList = true,
            nameTransformer = _ => notificationTypeName(model)
          )
        )(model.id)
    )
    ruleBasedTypeGenerator("Subscription", rules)
  }

  def mutationType: ObjectTypeDefinition = {
    val rules: List[HModel => FieldDefinition] = List(
      model =>
        graphQlField(
          modelId => "create" + modelId.capitalize,
          args = Map(
            model.id.toLowerCase -> fieldType(
              model,
              nameTransformer = inputTypeName(_)(ObjectInput)
            )
          ),
          fieldType = outputType(model)
        )(model.id),
      model =>
        graphQlField(
          modelId => "update" + modelId.capitalize,
          args = Map(
            model.primaryField.id -> fieldType(model.primaryField.htype),
            model.id.toLowerCase -> fieldType(
              model,
              nameTransformer = inputTypeName(_)(OptionalInput)
            )
          ),
          fieldType = outputType(model)
        )(model.id),
      model =>
        graphQlField(
          modelId => "upsert" + modelId.capitalize,
          args = Map(
            model.id.toLowerCase -> fieldType(
              model,
              nameTransformer = inputTypeName(_)(OptionalInput)
            )
          ),
          fieldType = outputType(model)
        )(model.id),
      model =>
        graphQlField(
          modelId => "delete" + modelId.capitalize,
          args =
            Map(model.primaryField.id -> fieldType(model.primaryField.htype)),
          fieldType = outputType(model)
        )(model.id),
      model =>
        graphQlField(
          nameTransformer =
            _ => "createMany" + Pluralizer.pluralize(model).capitalize,
          args = Map(
            Pluralizer.pluralize(model).toLowerCase -> listFieldType(
              model,
              nameTransformer = inputTypeName(_)(ObjectInput)
            )
          ),
          fieldType = outputType(model, isList = true)
        )(model.id),
      model =>
        graphQlField(
          nameTransformer =
            _ => "updateMany" + Pluralizer.pluralize(model).capitalize,
          args = Map(
            Pluralizer.pluralize(model).toLowerCase -> listFieldType(
              model,
              nameTransformer = inputTypeName(_)(ReferenceInput)
            )
          ),
          fieldType = outputType(model, isList = true)
        )(model.id),
      model =>
        graphQlField(
          nameTransformer =
            _ => "upsertMany" + Pluralizer.pluralize(model).capitalize,
          args = Map(
            Pluralizer.pluralize(model).toLowerCase -> listFieldType(
              model,
              nameTransformer = inputTypeName(_)(OptionalInput)
            )
          ),
          fieldType = outputType(model, isList = true)
        )(model.id),
      model =>
        graphQlField(
          _ => "deleteMany" + Pluralizer.pluralize(model).capitalize,
          args = Map(
            model.primaryField.id -> listFieldType(model.primaryField.htype)
          ),
          fieldType = outputType(model, isList = true)
        )(model.id)
    )

    ruleBasedTypeGenerator("Mutation", rules)
  }

  def buildApiSchema = Document(
    (queryType
      :: mutationType
      :: subscriptionType
      :: buitlinGraphQlTypeDefinitions
      ::: outputTypes
      ::: inputTypes(ObjectInput)
      ::: inputTypes(ReferenceInput)
      ::: inputTypes(OptionalInput)
      ::: notificationTypes).toVector
  )
}

object DefualtApiSchemaGenerator {
  sealed trait InputKind
  object ObjectInput extends InputKind
  object ReferenceInput extends InputKind
  object OptionalInput extends InputKind

  sealed trait BuiltinGraphQlType
  object EqInput extends BuiltinGraphQlType
  object WhereInput extends BuiltinGraphQlType
  object OrderByInput extends BuiltinGraphQlType
  object OrderEnum extends BuiltinGraphQlType
  object RangeInput extends BuiltinGraphQlType
  object LogicalFilterInput extends BuiltinGraphQlType
  object FilterInput extends BuiltinGraphQlType
  object MultiRecordEvent extends BuiltinGraphQlType
  object SingleRecordEvent extends BuiltinGraphQlType
  object AnyScalar extends BuiltinGraphQlType
  object GqlInt extends BuiltinGraphQlType

  def typeBuilder[T](
      typeNameCallback: T => String
  )(
      t: T,
      isOptional: Boolean,
      isList: Boolean
  ) =
    isOptional match {
      case true if isList  => ListType(NamedType(typeNameCallback(t)))
      case true if !isList => NamedType(typeNameCallback(t))
      case false if isList =>
        NotNullType(ListType(NamedType(typeNameCallback(t))))
      case false if !isList => NotNullType(NamedType(typeNameCallback(t)))
    }

  def builtinTypeName(t: BuiltinGraphQlType): String = t match {
    case MultiRecordEvent   => "MultiRecordEvent"
    case OrderEnum          => "OrderEnum"
    case SingleRecordEvent  => "SingleRecordEvent"
    case WhereInput         => "WhereInput"
    case EqInput            => "EqInput"
    case OrderByInput       => "OrderByInput"
    case FilterInput        => "FilterInput"
    case AnyScalar          => "Any"
    case LogicalFilterInput => "LogicalFilterInput"
    case RangeInput         => "RangeInput"
    case GqlInt             => "Int"
  }

  def builtinType(
      t: BuiltinGraphQlType,
      isOptional: Boolean = false,
      isList: Boolean = false
  ): Type = typeBuilder(builtinTypeName)(t, isOptional, isList)

  def outputType(
      model: HModel,
      isOptional: Boolean = false,
      isList: Boolean = false,
      nameTransformer: String => String = identity
  ): Type =
    typeBuilder((model: HModel) => nameTransformer(model.id))(
      model,
      isOptional,
      isList
    )

  def inputKindSuffix(kind: InputKind) = kind match {
    case ObjectInput    => "ObjectInput"
    case OptionalInput  => "OptionalInput"
    case ReferenceInput => "ReferenceInput"
  }

  def notificationTypeName(modelId: String): String =
    s"${modelId.capitalize}Notification"
  def notificationTypeName(model: HModel): String =
    notificationTypeName(model.id)

  def inputTypeName(modelId: String)(kind: InputKind): String =
    modelId.capitalize + inputKindSuffix(kind)
  def inputTypeName(model: HModel)(kind: InputKind): String =
    inputTypeName(model.id)(kind)

  lazy val buitlinGraphQlTypeDefinitions =
    gql"""
      input EqInput {
        field: String!
        value: Any!
      }
      
      input WhereInput {
        filter: LogicalFilterInput
        orderBy: OrderByInput
        range: RangeInput
        first: Int
        last: Int
        skip: Int
      }
      
      input OrderByInput {
        field: String!
        order: OrderEnum
      }
      
      enum OrderEnum {
        DESC
        ASC
      }
      
      input RangeInput {
        before: ID!
        after: ID!
      }
    
      input LogicalFilterInput {
        AND: [LogicalFilterInput]
        OR: [LogicalFilterInput]
        predicate: FilterInput
      }
      
      input FilterInput {
        eq: EqInput
      }
      
      enum MultiRecordEvent {
        CREATE
        UPDATE
        READ
        DELETE
      }
      
      enum SingleRecordEvent {
        UPDATE
        READ
        DELETE
      }
      
      scalar Any
      """.definitions.toList
}
