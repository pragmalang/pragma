package setup.schemaGenerator

import domain._, primitives._, Implicits._
import sangria.ast._
import sangria.ast.{Directive => GraphQlDirective}
import sangria.macros._

case class ApiSchemaGenerator(syntaxTree: SyntaxTree) {
  import ApiSchemaGenerator._

  def buildGraphQLAst() = Document(typeDefinitions.toVector)

  def typeDefinitions(): List[Definition] =
    syntaxTree.models.map(hShape(_)) ++ syntaxTree.enums.map(hEnum(_))

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

  def modelMutationsTypes: List[Definition] =
    syntaxTree.models.map(model => {

      val login = model.isUser match {
        case true => {
          val secretCredentialField = model.fields
            .find(
              f => f.directives.exists(d => d.id == "secretCredential")
            )
            .get
          Some(
            graphQlField(
              nameTransformer = _ => "login",
              args = Map(
                "publicCredential" -> builtinType(StringScalar),
                secretCredentialField.id -> fieldType(
                  secretCredentialField.ptype
                )
              ),
              fieldType = builtinType(StringScalar)
            )(model.id)
          )
        }
        case false => None
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

      val recover = Some(
        graphQlField(
          nameTransformer = _ => "recover",
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
              isEmptiable = false
            )
          ),
          fieldType = listFieldType(model)
        )(model.id)
      )

      val recoverMany = Some(
        graphQlField(
          nameTransformer = _ => "recoverMany",
          args = Map(
            "items" -> listFieldType(
              model.primaryField.ptype,
              isEmptiable = false
            )
          ),
          fieldType = listFieldType(model)
        )(model.id)
      )

      val modelListFields = model.fields.collect(
        f =>
          f.ptype match {
            case _: PArray => f
          }
      )

      val modelListFieldOperations = modelListFields
        .flatMap(f => {
          val listFieldInnerType = f.ptype.asInstanceOf[PArray].ptype
          val transformedFieldId =
            if(modelListFields.filter(_.id.toLowerCase == f.id.toLowerCase).length > 1)
              f.id
            else
              f.id.capitalize

          val pushTo = Some(
            graphQlField(
              nameTransformer = _ => s"pushTo${transformedFieldId}",
              Map(
                "item" -> fieldType(
                  listFieldInnerType,
                  nameTransformer =
                    fieldTypeName => s"${fieldTypeName.capitalize}Input"
                )
              ),
              fieldType(listFieldInnerType)
            )(f.id)
          )

          val removeFrom = Some(
            graphQlField(
              nameTransformer = fieldId => s"removeFrom${transformedFieldId}",
              Map(
                "item" -> fieldType(
                  listFieldInnerType match {
                    case m: PModel => m.primaryField.ptype
                    case t         => t
                  },
                  nameTransformer =
                    fieldTypeName => s"${fieldTypeName.capitalize}Input"
                )
              ),
              fieldType(listFieldInnerType)
            )(f.id)
          )

          val pushManyTo = Some(
            graphQlField(
              nameTransformer = fieldId => s"pushManyTo${transformedFieldId}",
              Map(
                "item" -> listFieldType(
                  listFieldInnerType,
                  nameTransformer =
                    fieldTypeName => s"${fieldTypeName.capitalize}Input",
                  isEmptiable = false
                )
              ),
              fieldType(f.ptype)
            )(f.id)
          )

          val removeManyFrom = Some(
            graphQlField(
              nameTransformer =
                fieldId => s"removeManyFrom${transformedFieldId}",
              Map(
                "item" -> listFieldType(
                  listFieldInnerType match {
                    case m: PModel => m.primaryField.ptype
                    case t         => t
                  },
                  nameTransformer =
                    fieldTypeName => s"${fieldTypeName.capitalize}Input",
                  isEmptiable = false
                )
              ),
              fieldType(f.ptype)
            )(f.id)
          )

          List(pushTo, pushManyTo, removeFrom, removeManyFrom)
        })
        .toVector

      val fields: Vector[FieldDefinition] = (Vector(
        login,
        create,
        update,
        delete,
        recover,
        createMany,
        updateMany,
        deleteMany,
        recoverMany
      ) :++ modelListFieldOperations).collect { case Some(value) => value }

      ObjectTypeDefinition(s"${model.id}Mutations", Vector.empty, fields)
    })

  def modelQueriesTypes: List[Definition] =
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

  def modelSubscriptionsTypes: List[Definition] =
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
    val PReferenceType = fieldType(
      ht = field.ptype,
      nameTransformer = inputTypeName(_)(OptionalInput),
      isOptional = true
    )

    val isReferenceToModel = (t: PReferenceType) =>
      syntaxTree.models.exists(_.id == t.id)

    field.ptype match {
      case t: PReferenceType if isReferenceToModel(t) =>
        PReferenceType
      case PArray(t: PReferenceType) if isReferenceToModel(t) =>
        PReferenceType
      case POption(t: PReferenceType) if isReferenceToModel(t) =>
        PReferenceType
      case _ =>
        fieldType(
          ht = field.ptype,
          isOptional = true
        )
    }
  }

  def inputTypes: List[Definition] =
    syntaxTree.models.map { model =>
      InputObjectTypeDefinition(
        name = inputTypeName(model)(OptionalInput),
        fields = model.fields.toVector.map(
          field =>
            InputValueDefinition(
              name = field.id,
              valueType = inputFieldType(field),
              None
            )
        )
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

  def gqlFieldToHModelField(
      field: Either[FieldDefinition, InputValueDefinition]
  ): PModelField = {
    def fieldType(
        tpe: Type,
        fieldPType: PType,
        isOptional: Boolean = true
    ): PType =
      tpe match {
        case ListType(ofType, _) =>
          PArray(fieldType(ofType, fieldPType))
        case NamedType(name, _) =>
          if (isOptional) POption(fieldPType) else fieldPType
        case NotNullType(ofType, _) => fieldType(ofType, fieldPType, false)
      }

    field match {
      case Left(field) => {
        val fieldPType = gqlTypeToPType(
          getTypeFromSchema(field.fieldType.namedType).get.typeDef
        )
        PModelField(
          id = field.name,
          ptype = fieldType(field.fieldType, fieldPType),
          None,
          Nil,
          None
        )
      }
      case Right(value) => {
        val fieldPType = gqlTypeToPType(
          getTypeFromSchema(value.valueType.namedType).get.typeDef
        )
        PModelField(
          id = value.name,
          ptype = fieldType(value.valueType, fieldPType),
          None,
          Nil,
          None
        )
      }
    }
  }

  def gqlTypeToPType(
      typeDef: TypeDefinition,
      isReference: Boolean = true
  ): PType = typeDef match {
    case i: InterfaceTypeDefinition if isReference => PReference(i.name)
    case i: InterfaceTypeDefinition if !isReference =>
      PModel(
        id = i.name,
        fields = i.fields.map(f => gqlFieldToHModelField(Left(f))).toList,
        Nil,
        None
      )
    case e: EnumTypeDefinition =>
      PEnum(id = e.name, values = e.values.map(v => v.name).toList, None)
    case s: ScalarTypeDefinition if s.name == "Int"     => PInt
    case s: ScalarTypeDefinition if s.name == "String"  => PString
    case s: ScalarTypeDefinition if s.name == "Float"   => PFloat
    case s: ScalarTypeDefinition if s.name == "Boolean" => PBool
    case s: ScalarTypeDefinition if s.name == "ID"      => PString
    case s: ScalarTypeDefinition if s.name == "Any"     => PAny
    case _: UnionTypeDefinition =>
      throw new Exception(
        "GraphQL unions types can't be converted to Pragma types"
      )
    case o: ObjectTypeDefinition if isReference => PReference(o.name)
    case o: ObjectTypeDefinition if !isReference =>
      PModel(
        id = o.name,
        fields = o.fields.map(f => gqlFieldToHModelField(Left(f))).toList,
        Nil,
        None
      )
    case i: InputObjectTypeDefinition if isReference => PReference(i.name)
    case i: InputObjectTypeDefinition if !isReference =>
      PModel(
        id = i.name,
        fields = i.fields.map(f => gqlFieldToHModelField(Right(f))).toList,
        Nil,
        None
      )
  }

  def build(
      as: SchemaBuildOutput = AsSyntaxTree
  ): Either[SyntaxTree, Document] = as match {
    case AsSyntaxTree => Left(buildApiSchemaAsSyntaxTree)
    case AsDocument   => Right(buildApiSchemaAsDocument)
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
      .map(t => gqlTypeToPType(t, false))
      .collect { case t: PConstruct => t }
      .toList
    SyntaxTree.fromConstructs(definitions)
  }

  lazy val buildApiSchemaAsDocument = Document {
    (queryType
      :: mutationType
      :: subscriptionType
      :: buitlinGraphQlDefinitions
      ::: outputTypes
      ::: inputTypes
      ::: modelMutationsTypes
      ::: modelQueriesTypes
      ::: modelSubscriptionsTypes).toVector
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
  object LogicalFilterInput extends BuiltinGraphQlType
  object FilterInput extends BuiltinGraphQlType
  object MultiRecordEvent extends BuiltinGraphQlType
  object SingleRecordEvent extends BuiltinGraphQlType
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
    case MultiRecordEvent   => "MultiRecordEvent"
    case OrderEnum          => "OrderEnum"
    case SingleRecordEvent  => "SingleRecordEvent"
    case WhereInput         => "WhereInput"
    case EqInput            => "EqInput"
    case OrderByInput       => "OrderByInput"
    case FilterInput        => "FilterInput"
    case LogicalFilterInput => "LogicalFilterInput"
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
      case PSelf(id) =>
        if (isOptional) NamedType(nameTransformer(id))
        else NotNullType(NamedType(nameTransformer(id)))
      case PFunction(args, returnType) =>
        throw new Exception("Function can't be used as a field type")
    }

  def hEnum(e: PEnum): EnumTypeDefinition =
    EnumTypeDefinition(
      name = e.id,
      values = e.values.map(v => EnumValueDefinition(name = v)).toVector
    )

  def hShape(
      s: PShape,
      directives: Vector[GraphQlDirective] = Vector.empty
  ): ObjectTypeDefinition = ObjectTypeDefinition(
    s.id,
    Vector.empty,
    s.fields
      .filter({
        case field: PInterfaceField => true
        case field: PModelField =>
          !field.directives.exists(_.id == "secretCredential")
      })
      .map(hShapeField)
      .toVector,
    directives
  )

  def hShapeField(
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
      field: String! 
      regex: String!
    }

    input ComparisonInput {
      # could be a single field like "friend" or a path "friend.name"
      # If the type of the field or the path is object,
      # then all fields that exist on value of `value: Any!` must be
      # compared with fields with the same name in the model recursively  
      field: String! 
      value: Any!
    }

    enum EVENT_ENUM {
      REMOVE
      NEW
      CHANGE
    }

    scalar Any

    directive @filter(filter: FilterInput!) on FIELD
    directive @order(order: OrderEnum!) on FIELD
    directive @range(range: RangeInput!) on FIELD
    directive @first(first: Int!) on FIELD
    directive @last(last: Int!) on FIELD
    directive @skip(skip: Int!) on FIELD
    directive @listen(to: EVENT_ENUM!) on FIELD # on field selections inside a subscription
      """.definitions.toList
}
