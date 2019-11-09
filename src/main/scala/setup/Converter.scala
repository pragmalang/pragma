package setup

import domain._, primitives._, utils._

import sangria.validation.ValueCoercionViolation
import sangria.marshalling.DateSupport
import sangria.ast._
import sangria.schema.{ObjectType, Schema}
import sangria.macros._

trait Converter {
  type Def
  type EnumDef
  type ShapeDef
  type FieldTypeDef
  type FieldDef

  val syntaxTree: SyntaxTree

  def typeDefinitions(): List[Def]
  def fieldType(
      ht: HType,
      isOptional: Boolean,
      nameTransformer: String => String
  ): FieldTypeDef
  def hEnum(e: HEnum): EnumDef
  def hShape(s: HShape): ShapeDef
  def hShapeField(f: HShapeField): FieldDef

}

case class GraphQlConverter(syntaxTree: SyntaxTree) extends Converter {
  import GraphQlConverter._

  override type Def = Definition
  override type EnumDef = EnumTypeDefinition
  override type ShapeDef = ObjectTypeDefinition
  override type FieldTypeDef = Type
  override type FieldDef = FieldDefinition

  def buildGraphQLAst() = Document(typeDefinitions.toVector)

  def buildGraphQLSchemaAst(
      query: ObjectTypeDefinition = ObjectTypeDefinition(
        name = "Query",
        interfaces = Vector.empty,
        fields =
          Vector(FieldDefinition("stub", NamedType("String"), Vector.empty))
      ),
      mutation: Option[ObjectTypeDefinition] = None,
      subscription: Option[ObjectTypeDefinition] = None
  ) = mutation match {
    case None =>
      subscription match {
        case None => Document(typeDefinitions.toVector :+ query)
        case Some(subscription) =>
          Document(typeDefinitions.toVector :+ query :+ subscription)
      }
    case Some(mutation) =>
      subscription match {
        case None => Document(typeDefinitions.toVector :+ query :+ mutation)
        case Some(subscription) =>
          Document(
            typeDefinitions.toVector :+ query :+ mutation :+ subscription
          )
      }
  }

  override def typeDefinitions(): List[Definition] =
    syntaxTree.models.map(hShape(_)) ++ syntaxTree.enums.map(hEnum(_))

  override def fieldType(
      ht: HType,
      isOptional: Boolean = false,
      nameTransformer: String => String = identity
  ): Type =
    ht match {
      case HArray(ht) =>
        if (isOptional) ListType(fieldType(ht, isOptional = true))
        else NotNullType(ListType(fieldType(ht, isOptional = true)))
      case HBool =>
        if (isOptional) NamedType("Boolean")
        else NotNullType(NamedType("Boolean"))
      case HDate =>
        if (isOptional) NamedType("Date") else NotNullType(NamedType("Date"))
      case HFloat =>
        if (isOptional) NamedType("Float") else NotNullType(NamedType("Float"))
      case HInteger =>
        if (isOptional) NamedType("Int") else NotNullType(NamedType("Int"))
      case HString =>
        if (isOptional) NamedType("String")
        else NotNullType(NamedType("String"))
      case HOption(ht) => fieldType(ht, isOptional = true)
      case HFile(_, _) =>
        if (isOptional) NamedType("String")
        else NotNullType(NamedType("String"))
      case s: HShape =>
        if (isOptional) NamedType(nameTransformer(s.id))
        else NotNullType(NamedType(nameTransformer(s.id)))
      case e: HEnum =>
        if (isOptional) NamedType(e.id) else NotNullType(NamedType(e.id))
      case HReference(id) =>
        if (isOptional) NamedType(nameTransformer(id))
        else NotNullType(NamedType(nameTransformer(id)))
      case HSelf(id) =>
        if (isOptional) NamedType(nameTransformer(id))
        else NotNullType(NamedType(nameTransformer(id)))
      case HFunction(args, returnType) =>
        throw new Exception("Function can't be used as a field type")
    }

  override def hEnum(e: HEnum): EnumTypeDefinition =
    EnumTypeDefinition(
      name = e.id,
      values = e.values.map(v => EnumValueDefinition(name = v)).toVector
    )

  override def hShape(s: HShape): ObjectTypeDefinition = ObjectTypeDefinition(
    s.id,
    Vector.empty,
    s.fields.map(hShapeField).toVector
  )

  override def hShapeField(
      f: HShapeField
  ): FieldDefinition = FieldDefinition(
    name = f.id,
    fieldType = fieldType(f.htype),
    Vector.empty
  )

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

object GraphQlConverter {
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
