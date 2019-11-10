package setup
import domain._, primitives._, utils._

import sangria.ast._
import sangria.macros._
import ApiSchemaGenerator._

case class ApiSchemaGenerator(override val syntaxTree: SyntaxTree)
    extends GraphQlConverter(syntaxTree) {

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

  def outputTypes: List[Definition] = typeDefinitions map {
    case objDef: ObjectTypeDefinition =>
      objDef.copy(fields = objDef.fields map { field =>
        field.fieldType match {
          case t: ListType =>
            field.copy(
              arguments =
                graphQlFieldArgs(Map("where" -> NamedType("WhereInput")))
            )
          case t => field
        }
      })
    case td => td
  }

  def inputTypes(kind: InputKind): List[Definition] =
    syntaxTree.models.map { model =>
      InputObjectTypeDefinition(
        name = model.id.capitalize + inputKindSuffix(kind),
        fields = model.fields.toVector.map { field =>
          InputValueDefinition(
            name = field.id,
            valueType = fieldType(
              ht = field.htype,
              nameTransformer = name =>
                name.capitalize + inputKindSuffix(
                  ReferenceInput
                ),
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
        name = s"${model.id.capitalize}Notification",
        interfaces = Vector.empty,
        fields = Vector(
          FieldDefinition(
            name = "event",
            fieldType = NotNullType(NamedType("Event")),
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
          fieldType = fieldType(model)
        )(model.id),
      model =>
        graphQlField(
          _ => Pluralizer.pluralize(model).toLowerCase,
          args = Map("where" -> NamedType("WhereInput")),
          fieldType = ListType(fieldType(model))
        )(model.id),
      model =>
        graphQlField(
          _ => "count" + Pluralizer.pluralize(model).capitalize,
          args = Map("where" -> NamedType("WhereInput")),
          fieldType = NamedType("Int")
        )(model.id),
      model =>
        graphQlField(
          _ => model.id.toLowerCase + "Exists",
          args = Map("filter" -> NamedType("LogicalFilterInput")),
          fieldType = NamedType("Int")
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
            "on" -> ListType(NotNullType(NamedType("SingleRecordEvent")))
          ),
          fieldType = fieldType(
            model,
            nameTransformer = name => name.capitalize + "Notification"
          )
        )(model.id),
      model =>
        graphQlField(
          _ => Pluralizer.pluralize(model).toLowerCase,
          args = Map(
            "where" -> NamedType("WhereInput"),
            "on" -> ListType(NotNullType(NamedType("MultiRecordEvent")))
          ),
          fieldType = ListType(
            fieldType(
              model,
              nameTransformer = name => name.capitalize + "Notification"
            )
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
              nameTransformer =
                name => name.capitalize + inputKindSuffix(ObjectInput)
            )
          ),
          fieldType = fieldType(model)
        )(model.id),
      model =>
        graphQlField(
          modelId => "update" + modelId.capitalize,
          args = Map(
            model.primaryField.id -> fieldType(model.primaryField.htype),
            model.id.toLowerCase -> fieldType(
              model,
              nameTransformer =
                name => name.capitalize + inputKindSuffix(OptionalInput)
            )
          ),
          fieldType = fieldType(model)
        )(model.id),
      model =>
        graphQlField(
          modelId => "upsert" + modelId.capitalize,
          args = Map(
            model.id.toLowerCase -> fieldType(
              model,
              nameTransformer =
                name => name.capitalize + inputKindSuffix(OptionalInput)
            )
          ),
          fieldType = fieldType(model)
        )(model.id),
      model =>
        graphQlField(
          modelId => "delete" + modelId.capitalize,
          args =
            Map(model.primaryField.id -> fieldType(model.primaryField.htype)),
          fieldType = fieldType(model)
        )(model.id),
      model =>
        graphQlField(
          nameTransformer =
            _ => "createMany" + Pluralizer.pluralize(model).capitalize,
          args = Map(
            Pluralizer.pluralize(model).toLowerCase -> ListType(
              fieldType(
                model,
                nameTransformer =
                  name => name.capitalize + inputKindSuffix(ObjectInput)
              )
            )
          ),
          fieldType = ListType(fieldType(model))
        )(model.id),
      model =>
        graphQlField(
          nameTransformer =
            _ => "updateMany" + Pluralizer.pluralize(model).capitalize,
          args = Map(
            Pluralizer.pluralize(model).toLowerCase -> ListType(
              fieldType(
                model,
                nameTransformer =
                  name => name.capitalize + inputKindSuffix(ReferenceInput)
              )
            )
          ),
          fieldType = ListType(fieldType(model))
        )(model.id),
      model =>
        graphQlField(
          nameTransformer =
            _ => "upsertMany" + Pluralizer.pluralize(model).capitalize,
          args = Map(
            Pluralizer.pluralize(model).toLowerCase -> ListType(
              fieldType(
                model,
                nameTransformer =
                  name => name.capitalize + inputKindSuffix(OptionalInput)
              )
            )
          ),
          fieldType = ListType(fieldType(model))
        )(model.id),
      model =>
        graphQlField(
          _ => "deleteMany" + Pluralizer.pluralize(model).capitalize,
          args = Map(
            model.primaryField.id -> ListType(
              fieldType(model.primaryField.htype)
            )
          ),
          fieldType = ListType(fieldType(model))
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

object ApiSchemaGenerator {
  sealed trait InputKind
  object ObjectInput extends InputKind
  object ReferenceInput extends InputKind
  object OptionalInput extends InputKind

  def inputKindSuffix(kind: InputKind) = kind match {
    case ObjectInput    => "ObjectInput"
    case OptionalInput  => "OptionalInput"
    case ReferenceInput => "ReferenceInput"
  }

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
