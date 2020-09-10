package setup.schemaGenerator

import domain._, DomainImplicits._
import sangria.ast._
import sangria.ast.{Directive => GraphQlDirective}
import sangria.macros._

case class ApiSchemaGenerator(syntaxTree: SyntaxTree) {
  import ApiSchemaGenerator._

  def buildGraphQLAst() = Document(typeDefinitions.toVector)

  def typeDefinitions(): Iterable[Definition] =
    syntaxTree.models.map(pshape(_)) ++
      syntaxTree.enums.map(penum(_))

  def graphQlFieldArgs(args: Map[String, Type]) =
    args.map(arg => InputValueDefinition(arg._1, arg._2, None)).toVector

  def graphQlField(
      nameTransformer: String => String = identity,
      args: Map[String, Type] = Map.empty,
      fieldType: Type
  )(name: String) = FieldDefinition(
    name = nameTransformer(name),
    fieldType = fieldType,
    arguments = graphQlFieldArgs(args)
  )

  def outputTypes: Iterable[Definition] = typeDefinitions map {
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

  def modelMutationsTypes: Iterable[Definition] =
    syntaxTree.models.map(model => {

      val publicCredentialFields = model.fields.filter(_.isPublicCredential)

      val logins = publicCredentialFields.map {
        field =>
          val secretCredentialField = model.fields
            .find(_.isSecretCredential)

          val transformedFieldId = field.id.capitalize

          if (model.isUser)
            Some(
              graphQlField(
                nameTransformer = _ => s"loginBy${transformedFieldId}",
                args = secretCredentialField match {
                  case None =>
                    Map(field.id -> builtinType(StringScalar))
                  case Some(secretCredentialField) =>
                    Map(
                      field.id -> builtinType(StringScalar),
                      secretCredentialField.id ->
                        fieldType(secretCredentialField.ptype)
                    )
                },
                fieldType = builtinType(StringScalar)
              )(model.id)
            )
          else None
      }

      val create = Some(
        graphQlField(
          nameTransformer = _ => "create",
          args = Map(
            model.id.small -> fieldType(
              model,
              nameTransformer = inputTypeName(_)(OptionalInput)
            )
          ),
          fieldType = fieldType(model)
        )(model.id)
      )

      val update = Some(
        graphQlField(
          nameTransformer = _ => "update",
          args = Map(
            model.primaryField.id -> fieldType(model.primaryField.ptype),
            model.id.small -> fieldType(
              model,
              nameTransformer = inputTypeName(_)(OptionalInput)
            )
          ),
          fieldType = fieldType(model)
        )(model.id)
      )

      val delete = Some(
        graphQlField(
          nameTransformer = _ => "delete",
          args = Map(
            model.primaryField.id -> fieldType(model.primaryField.ptype)
          ),
          fieldType = fieldType(model)
        )(model.id)
      )

      val createMany = Some(
        graphQlField(
          nameTransformer = _ => "createMany",
          args = Map(
            "items" -> listFieldType(
              model,
              nameTransformer = inputTypeName(_)(OptionalInput),
              isEmptiable = false
            )
          ),
          fieldType = listFieldType(model)
        )(model.id)
      )

      val updateMany = Some(
        graphQlField(
          nameTransformer = _ => "updateMany",
          args = Map(
            "items" -> listFieldType(
              model,
              nameTransformer = inputTypeName(_)(OptionalInput),
              isEmptiable = false
            )
          ),
          fieldType = listFieldType(model)
        )(model.id)
      )

      val deleteMany = Some(
        graphQlField(
          nameTransformer = _ => "deleteMany",
          args = Map(
            "items" -> listFieldType(
              model.primaryField.ptype,
              isEmptiable = false,
              isOptional = true
            )
          ),
          fieldType = listFieldType(model)
        )(model.id)
      )

      val modelListFields = model.fields.collect {
        case f @ PModelField(_, _: PArray, _, _, _, _) => f
      }

      val modelListFieldOperations = modelListFields
        .flatMap(f => {
          val listFieldInnerType = f.ptype.asInstanceOf[PArray].ptype
          val transformedFieldId = f.id.capitalize

          val pushTo = Some(
            graphQlField(
              nameTransformer = _ => s"pushTo${transformedFieldId}",
              Map(
                model.primaryField.id -> fieldType(model.primaryField.ptype),
                "item" -> fieldType(
                  listFieldInnerType,
                  nameTransformer =
                    fieldTypeName => s"${fieldTypeName.capitalize}Input"
                )
              ),
              fieldType(model)
            )(f.id)
          )

          val removeFrom = Some(
            graphQlField(
              nameTransformer = _ => s"removeFrom${transformedFieldId}",
              Map(
                model.primaryField.id -> fieldType(model.primaryField.ptype),
                "item" -> fieldType(
                  listFieldInnerType match {
                    case m: PModel => m.primaryField.ptype
                    case PReference(id) =>
                      syntaxTree.modelsById(id).primaryField.ptype
                    case t => t
                  },
                  nameTransformer =
                    fieldTypeName => s"${fieldTypeName.capitalize}Input"
                )
              ),
              fieldType(model)
            )(f.id)
          )

          val pushManyTo = Some(
            graphQlField(
              nameTransformer = _ => s"pushManyTo${transformedFieldId}",
              Map(
                model.primaryField.id -> fieldType(model.primaryField.ptype),
                "items" -> listFieldType(
                  listFieldInnerType,
                  nameTransformer =
                    fieldTypeName => s"${fieldTypeName.capitalize}Input",
                  isEmptiable = false
                )
              ),
              fieldType(model)
            )(f.id)
          )

          val removeManyFrom = Some(
            graphQlField(
              nameTransformer = _ => s"removeManyFrom${transformedFieldId}",
              Map(
                model.primaryField.id -> fieldType(model.primaryField.ptype),
                "filter" -> builtinType(FilterInput, isOptional = true)
              ),
              fieldType(model)
            )(f.id)
          )

          List(pushTo, pushManyTo, removeFrom, removeManyFrom)
        })
        .toVector

      val fields: Vector[FieldDefinition] = (Vector(
        create,
        update,
        delete,
        createMany,
        updateMany,
        deleteMany
      ) :++ modelListFieldOperations :++ logins).collect {
        case Some(value) => value
      }

      ObjectTypeDefinition(s"${model.id}Mutations", Vector.empty, fields)
    })

  def modelQueriesTypes: Iterable[Definition] =
    syntaxTree.models.map(model => {

      val read = Some(
        graphQlField(
          nameTransformer = _ => "read",
          args = Map(
            model.primaryField.id -> fieldType(model.primaryField.ptype)
          ),
          fieldType = fieldType(model, isOptional = true)
        )(model.id)
      )

      val list = Some(
        graphQlField(
          nameTransformer = _ => "list",
          args = Map(
            "where" -> builtinType(WhereInput, isOptional = true)
          ),
          fieldType = listFieldType(model)
        )(model.id)
      )

      val fields: Vector[FieldDefinition] = Vector(read, list).collect {
        case Some(value) => value
      }

      ObjectTypeDefinition(s"${model.id}Queries", Vector.empty, fields)
    })

  def modelSubscriptionsTypes: Iterable[Definition] =
    syntaxTree.models.map(model => {

      val read = Some(
        graphQlField(
          nameTransformer = _ => "read",
          args = Map(
            model.primaryField.id -> fieldType(model.primaryField.ptype)
          ),
          fieldType = fieldType(model, isOptional = true)
        )(model.id)
      )

      val list = Some(
        graphQlField(
          nameTransformer = _ => "list",
          args = Map(
            "where" -> builtinType(WhereInput, isOptional = true)
          ),
          fieldType = fieldType(model, isOptional = true)
        )(model.id)
      )

      val fields: Vector[FieldDefinition] = Vector(read, list).collect {
        case Some(value) => value
      }

      ObjectTypeDefinition(s"${model.id}Subscriptions", Vector.empty, fields)
    })

  def inputFieldType(field: PModelField) = {
    val pReferenceType = fieldType(
      ht = field.ptype,
      nameTransformer = inputTypeName(_)(OptionalInput),
      isOptional = true
    )

    val isReferenceToModel = (t: PReference) =>
      syntaxTree.modelsById.get(t.id).isDefined

    field.ptype match {
      case t: PReference if isReferenceToModel(t) =>
        pReferenceType
      case PArray(t: PReference) if isReferenceToModel(t) =>
        pReferenceType
      case POption(t: PReference) if isReferenceToModel(t) =>
        pReferenceType
      case _ =>
        fieldType(
          ht = field.ptype,
          isOptional = true
        )
    }
  }

  def inputTypes: Iterable[Definition] =
    syntaxTree.models.map { model =>
      InputObjectTypeDefinition(
        name = inputTypeName(model)(OptionalInput),
        fields = model.fields.map { field =>
          InputValueDefinition(
            name = field.id,
            valueType = inputFieldType(field),
            None
          )
        }.toVector
      )
    }

  def ruleBasedTypeGenerator(
      typeName: String,
      rules: List[PModel => Option[FieldDefinition]]
  ) = ObjectTypeDefinition(
    name = typeName,
    interfaces = Vector.empty,
    fields = rules
      .foldLeft(List.empty[Option[FieldDefinition]])(
        (acc, rule) => acc ++ syntaxTree.models.map(rule)
      )
      .collect {
        case Some(value) => value
      }
      .toVector
  )

  def queryType: ObjectTypeDefinition = {
    val rules: List[PModel => Option[FieldDefinition]] = List(
      model =>
        Some(
          graphQlField(fieldType = NamedType(s"${model.id}Queries"))(model.id)
        )
    )
    ruleBasedTypeGenerator("Query", rules)
  }

  def subscriptionType: ObjectTypeDefinition = {
    val rules: List[PModel => Option[FieldDefinition]] = List(
      model =>
        Some(
          graphQlField(fieldType = NamedType(s"${model.id}Subscriptions"))(
            model.id
          )
        )
    )
    ruleBasedTypeGenerator("Subscription", rules)
  }

  def mutationType: ObjectTypeDefinition = {
    val rules: List[PModel => Option[FieldDefinition]] = List(
      model =>
        Some(
          graphQlField(fieldType = NamedType(s"${model.id}Mutations"))(model.id)
        )
    )

    ruleBasedTypeGenerator("Mutation", rules)
  }

  def build = Document {
    (queryType
      :: mutationType
      :: subscriptionType
      :: buitlinGraphQlDefinitions
        ++ outputTypes
        ++ inputTypes
        ++ modelMutationsTypes
        ++ modelQueriesTypes
      ++ modelSubscriptionsTypes).toVector
  }
}

object ApiSchemaGenerator {
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
  object FilterInput extends BuiltinGraphQlType
  object AnyScalar extends BuiltinGraphQlType
  object IntScalar extends BuiltinGraphQlType
  object FloatScalar extends BuiltinGraphQlType
  object StringScalar extends BuiltinGraphQlType
  object BooleanScalar extends BuiltinGraphQlType
  object IDScalar extends BuiltinGraphQlType

  case class TypeFromSchema(
      typeDef: TypeDefinition,
      isEmptyList: Boolean,
      isNonEmptyList: Boolean,
      isOptional: Boolean,
      tpe: Type
  )

  trait SchemaBuildOutput
  case object AsDocument extends SchemaBuildOutput
  case object AsSyntaxTree extends SchemaBuildOutput

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
    case OrderEnum          => "OrderEnum"
    case WhereInput         => "WhereInput"
    case EqInput            => "EqInput"
    case OrderByInput       => "OrderByInput"
    case FilterInput        => "FilterInput"
    case RangeInput         => "RangeInput"
    case AnyScalar          => "Any"
    case IntScalar          => "Int"
    case StringScalar       => "String"
    case BooleanScalar      => "Boolean"
    case FloatScalar        => "Float"
    case IDScalar           => "ID"
  }

  def builtinType(
      t: BuiltinGraphQlType,
      isOptional: Boolean = false,
      isList: Boolean = false
  ): Type = typeBuilder(builtinTypeName)(t, isOptional, isList)

  def outputType(
      model: PModel,
      isOptional: Boolean = false,
      isList: Boolean = false,
      nameTransformer: String => String = identity
  ): Type =
    typeBuilder((model: PModel) => nameTransformer(model.id))(
      model,
      isOptional,
      isList
    )

  def inputKindSuffix(kind: InputKind) = kind match {
    case ObjectInput    => "ObjectInput"
    case OptionalInput  => "Input"
    case ReferenceInput => "ReferenceInput"
  }

  def notificationTypeName(modelId: String): String =
    s"${modelId.capitalize}Notification"
  def notificationTypeName(model: PModel): String =
    notificationTypeName(model.id)

  def inputTypeName(modelId: String)(kind: InputKind): String =
    modelId.capitalize + inputKindSuffix(kind)

  def inputTypeName(model: PModel)(kind: InputKind): String =
    inputTypeName(model.id)(kind)

  def listFieldType(
      ht: PType,
      isOptional: Boolean = false,
      isEmptiable: Boolean = true,
      nameTransformer: String => String = identity
  ): Type = (isOptional, isEmptiable) match {
    case (true, false) =>
      ListType(fieldType(ht, isOptional = false, nameTransformer))
    case (false, true) =>
      NotNullType(ListType(fieldType(ht, isOptional = true, nameTransformer)))
    case (true, true) =>
      ListType(fieldType(ht, isOptional = true, nameTransformer))
    case (false, false) =>
      NotNullType(ListType(fieldType(ht, isOptional = false, nameTransformer)))
  }

  def fieldType(
      ht: PType,
      isOptional: Boolean = false,
      nameTransformer: String => String = identity
  ): Type =
    ht match {
      case PArray(ht) =>
        if (isOptional)
          ListType(fieldType(ht, isOptional = true, nameTransformer))
        else
          NotNullType(
            ListType(fieldType(ht, isOptional = true, nameTransformer))
          )
      case PBool =>
        if (isOptional) NamedType("Boolean")
        else NotNullType(NamedType("Boolean"))
      case PDate =>
        if (isOptional) NamedType("Date") else NotNullType(NamedType("Date"))
      case PFloat =>
        if (isOptional) NamedType("Float") else NotNullType(NamedType("Float"))
      case PInt =>
        if (isOptional) NamedType("Int") else NotNullType(NamedType("Int"))
      case PString =>
        if (isOptional) NamedType("String")
        else NotNullType(NamedType("String"))
      case PAny =>
        if (isOptional) NamedType("Any")
        else NotNullType(NamedType("Any"))
      case POption(ht) => fieldType(ht, isOptional = true, nameTransformer)
      case PFile(_, _) =>
        if (isOptional) NamedType("String")
        else NotNullType(NamedType("String"))
      case s: PShape =>
        if (isOptional) NamedType(nameTransformer(s.id))
        else NotNullType(NamedType(nameTransformer(s.id)))
      case e: PEnum =>
        if (isOptional) NamedType(e.id) else NotNullType(NamedType(e.id))
      case PReference(id) =>
        if (isOptional) NamedType(nameTransformer(id))
        else NotNullType(NamedType(nameTransformer(id)))
      case PFunction(_, _) =>
        throw new Exception("Function can't be used as a field type")
    }

  def penum(e: PEnum): EnumTypeDefinition =
    EnumTypeDefinition(
      name = e.id,
      values = e.values.map(v => EnumValueDefinition(name = v)).toVector
    )

  def pshape(
      s: PShape,
      directives: Vector[GraphQlDirective] = Vector.empty
  ): ObjectTypeDefinition = ObjectTypeDefinition(
    s.id,
    Vector.empty,
    s.fields.collect {
      case field: PInterfaceField => pshapeField(field)
      case field: PModelField if !field.isSecretCredential =>
        pshapeField(field)
    }.toVector,
    directives
  )

  def pshapeField(
      f: PShapeField
  ): FieldDefinition = FieldDefinition(
    name = f.id,
    fieldType = fieldType(f.ptype),
    Vector.empty
  )

  lazy val buitlinGraphQlDefinitions =
    gql"""
    input WhereInput {
      filter: FilterInput
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

    enum EventEnum {
      REMOVE
      NEW
      CHANGE
    }

    enum OrderEnum {
      DESC
      ASC
    }

    input RangeInput {
      before: ID!
      after: ID!
    }

    input FilterInput {
      not: FilterInput
      and: FilterInput
      or: FilterInput
      eq: ComparisonInput # works only when the field is of type String or Int or Float
      gt: ComparisonInput # works only when the field is of type Float or Int
      gte: ComparisonInput # works only when the field is of type Float or Int
      lt: ComparisonInput # works only when the field is of type Float or Int
      lte: ComparisonInput # works only when the field is of type Float or Int
      matches: MatchesInput # works only when the field is of type String
    }

    input MatchesInput {
      # could be a single field like "friend" or a path "friend.name"
      # works only when the field is of type String
      field: String
      regex: String!
    }

    input ComparisonInput {
      # could be a single field like "friend" or a path "friend.name"
      # If the type of the field or the path is object,
      # then all fields that exist on value of `value: Any!` must be
      # compared with fields with the same name in the model recursively  
      field: String
      value: Any!
    }

    scalar Any

    directive @listen(to: EventEnum!) on FIELD # on field selections inside a subscription
      """.definitions.toList
}
