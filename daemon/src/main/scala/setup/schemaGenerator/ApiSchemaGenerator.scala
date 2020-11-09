package setup.schemaGenerator

import pragma.domain._, DomainImplicits._
import sangria.ast._
import sangria.ast.{Directive => GraphQlDirective}
import sangria.macros._
import pragma.domain.utils.Identifiable
import cats.implicits._

case class ApiSchemaGenerator(syntaxTree: SyntaxTree) {
  import ApiSchemaGenerator._

  def graphQlField(
      nameTransformer: String => String = identity,
      args: Map[String, Type] = Map.empty,
      fieldType: Type
  )(name: String) = FieldDefinition(
    name = nameTransformer(name),
    fieldType = fieldType,
    arguments = graphQlFieldArgs(args)
  )

  def outputTypes: Iterable[Definition] =
    syntaxTree.models.map(pshape(_)) ++ syntaxTree.enums.map(penum(_))

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
              nameTransformer = inputTypeName(_)(ModelInput)
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
              nameTransformer = inputTypeName(_)(ModelInput)
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
              nameTransformer = inputTypeName(_)(ModelInput)
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
              nameTransformer = inputTypeName(_)(ModelInput)
            )
          ),
          fieldType = listFieldType(model)
        )(model.id)
      )

      val deleteMany = Some(
        graphQlField(
          nameTransformer = _ => "deleteMany",
          args = Map(
            "items" -> listFieldType(model.primaryField.ptype)
          ),
          fieldType = listFieldType(model)
        )(model.id)
      )

      val modelListFieldOperations = model.fields.collect {
        case f @ PModelField(_, PArray(listFieldInnerType), _, _, _, _) => {
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
              fieldType(listFieldInnerType)
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
              fieldType(listFieldInnerType)
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
                    fieldTypeName => s"${fieldTypeName.capitalize}Input"
                )
              ),
              listFieldType(listFieldInnerType)
            )(f.id)
          )

          val removeManyFrom = Some(
            graphQlField(
              nameTransformer = _ => s"removeManyFrom${transformedFieldId}",
              Map(
                model.primaryField.id -> fieldType(model.primaryField.ptype),
                "filter" -> gqlType(
                  listFieldInnerType,
                  inputTypeName(_)(FilterInput)
                )
              ),
              listFieldType(listFieldInnerType)
            )(f.id)
          )

          List(pushTo, pushManyTo, removeFrom, removeManyFrom)
        }
      }.flatten

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
            "aggregation" -> gqlType(
              model,
              inputTypeName(_)(AggInput),
              isOptional = true
            )
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
            "aggregation" -> gqlType(
              model,
              inputTypeName(_)(AggInput),
              isOptional = true
            )
          ),
          fieldType = fieldType(model, isOptional = true)
        )(model.id)
      )

      val fields: Vector[FieldDefinition] = Vector(read, list).collect {
        case Some(value) => value
      }

      ObjectTypeDefinition(s"${model.id}Subscriptions", Vector.empty, fields)
    })

  def inputFieldType(field: PModelField, transformAll: Boolean = false)(
      inputKind: InputKind
  ) = {
    val transformedType = fieldType(
      ht = field.ptype,
      nameTransformer = inputTypeName(_)(inputKind),
      isOptional = true,
      transformAll = transformAll
    )
    field.ptype match {
      case _: PReference          => transformedType
      case PArray(_: PReference)  => transformedType
      case POption(_: PReference) => transformedType
      case _ if transformAll      => transformedType
      case _ =>
        fieldType(
          ht = field.ptype,
          isOptional = true
        )
    }
  }

  def inputTypes: Iterable[Definition] = {

    val modelsAndEnumsInputTypes =
      (syntaxTree.models ++ syntaxTree.enums).flatMap {
        modelOrEnum: Identifiable =>
          val predicateInput = modelOrEnum match {
            case model: PModel =>
              InputObjectTypeDefinition(
                name = inputTypeName(modelOrEnum.id)(PredicateInput),
                fields = model.fields
                  .filterNot(_.isSecretCredential)
                  .map { field =>
                    InputValueDefinition(
                      name = field.id,
                      valueType =
                        if (field.isArray)
                          namedType(
                            inputTypeName("Array")(PredicateInput),
                            isOptional = true
                          )
                        else
                          inputFieldType(field, transformAll = true)(
                            PredicateInput
                          ),
                      None
                    )
                  }
                  .toVector
              ).some
            case e: PEnum =>
              InputObjectTypeDefinition(
                name = inputTypeName(modelOrEnum.id)(PredicateInput),
                fields = Vector {
                  InputValueDefinition(
                    name = "eq",
                    valueType = gqlType(
                      e,
                      name => name + inputKindSuffix(PredicateInput),
                      isOptional = true
                    ),
                    None
                  )
                }
              ).some
            case _ => None
          }
          val filterInput = InputObjectTypeDefinition(
            name = inputTypeName(modelOrEnum.id)(FilterInput),
            fields = {
              val predicate = InputValueDefinition(
                name = "predicate",
                valueType =
                  namedType(inputTypeName(modelOrEnum.id)(PredicateInput)),
                None
              )

              val and = InputValueDefinition(
                name = "and",
                valueType = namedType(
                  inputTypeName(modelOrEnum.id)(FilterInput),
                  isOptional = true,
                  isList = true
                ),
                None
              )

              val or = InputValueDefinition(
                name = "or",
                valueType = namedType(
                  inputTypeName(modelOrEnum.id)(FilterInput),
                  isOptional = true,
                  isList = true
                ),
                None
              )

              val negated = InputValueDefinition(
                name = "negated",
                valueType = namedType(
                  builtinTypeName(BooleanScalar),
                  isOptional = true
                ),
                None
              )

              Vector(predicate, and, or, negated)
            }
          ).some
          val aggInput = InputObjectTypeDefinition(
            name = inputTypeName(modelOrEnum.id)(AggInput),
            fields = {
              val filter = InputValueDefinition(
                name = "filter",
                valueType = namedType(
                  inputTypeName(modelOrEnum.id)(FilterInput),
                  isOptional = true,
                  isList = true
                ),
                None
              )

              val from = InputValueDefinition(
                name = "from",
                valueType = namedType(
                  builtinTypeName(IntScalar),
                  isOptional = true
                ),
                None
              )

              val to = InputValueDefinition(
                name = "to",
                valueType = namedType(
                  builtinTypeName(IntScalar),
                  isOptional = true
                ),
                None
              )

              val orderBy = InputValueDefinition(
                name = "orderBy",
                valueType = namedType(
                  builtinTypeName(OrderByInput),
                  isOptional = true
                ),
                None
              )

              Vector(filter, from, to, orderBy)
            }
          ).some
          val input = modelOrEnum match {
            case model: PModel =>
              Some {
                InputObjectTypeDefinition(
                  name = inputTypeName(modelOrEnum.id)(ModelInput),
                  fields = model.fields.map { field =>
                    InputValueDefinition(
                      name = field.id,
                      valueType = inputFieldType(field)(ModelInput),
                      None
                    )
                  }.toVector
                )
              }
            case _ => None
          }

          Seq(predicateInput, filterInput, aggInput, input).collect {
            case Some(inputType) => inputType
          }
      }

    modelsAndEnumsInputTypes
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
  object ModelInput extends InputKind
  object AggInput extends InputKind
  object FilterInput extends InputKind
  object PredicateInput extends InputKind

  sealed trait BuiltinGraphQlType
  object OrderByInput extends BuiltinGraphQlType
  object OrderEnum extends BuiltinGraphQlType
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

  def typeBuilder[T](
      typeNameCallback: T => String
  )(
      t: T,
      isOptional: Boolean,
      isList: Boolean
  ) =
    (isOptional, isList) match {
      case (true, true)  => ListType(NotNullType(NamedType(typeNameCallback(t))))
      case (true, false) => NamedType(typeNameCallback(t))
      case (false, true) =>
        NotNullType(ListType(NotNullType(NamedType(typeNameCallback(t)))))
      case (false, false) => NotNullType(NamedType(typeNameCallback(t)))
    }

  def namedType(
      name: String,
      isOptional: Boolean = false,
      isList: Boolean = false
  ): Type = typeBuilder[String](identity)(name, isOptional, isList)

  def typeName(ptype: PType): String = ptype match {
    case PAny           => "Any"
    case PReference(id) => id
    case m: PModel      => m.id
    case i: PInterface  => i.id
    case e: PEnum       => e.id
    case PString        => "String"
    case PInt           => "Int"
    case PFloat         => "Float"
    case PBool          => "Boolean"
    case PDate          => "Date"
    case PArray(ptype)  => typeName(ptype)
    case _: PFile       => "File"
    case _: PFunction   => "Function"
    case POption(ptype) => typeName(ptype)
  }

  def builtinTypeName(t: BuiltinGraphQlType): String = t match {
    case OrderEnum     => "OrderEnum"
    case OrderByInput  => "OrderByInput"
    case IntScalar     => "Int"
    case StringScalar  => "String"
    case BooleanScalar => "Boolean"
    case FloatScalar   => "Float"
    case IDScalar      => "ID"
  }

  def builtinType(
      t: BuiltinGraphQlType,
      isOptional: Boolean = false,
      isList: Boolean = false
  ): Type = typeBuilder(builtinTypeName)(t, isOptional, isList)

  def gqlType(
      ptype: PType,
      nameTransformer: String => String = identity,
      isOptional: Boolean = false,
      isList: Boolean = false
  ): Type =
    typeBuilder[PType](t => nameTransformer(typeName(t)))(
      ptype,
      isOptional,
      isList
    )

  // def outputType(
  //     model: PModel,
  //     isOptional: Boolean = false,
  //     isList: Boolean = false,
  //     nameTransformer: String => String = identity
  // ): Type =
  //   typeBuilder((model: PModel) => nameTransformer(model.id))(
  //     model,
  //     isOptional,
  //     isList
  //   )

  def inputKindSuffix(kind: InputKind) = kind match {
    case ModelInput     => "Input"
    case PredicateInput => "Predicate"
    case AggInput       => "AggInput"
    case FilterInput    => "Filter"
  }

  def notificationTypeName(modelId: String): String =
    s"${modelId.capitalize}Notification"
  def notificationTypeName(model: PModel): String =
    notificationTypeName(model.id)

  def inputTypeName(name: String)(kind: InputKind): String =
    name.capitalize + inputKindSuffix(kind)

  def listFieldType(
      ht: PType,
      isOptional: Boolean = false,
      nameTransformer: String => String = identity
  ): Type =
    if (isOptional)
      ListType(fieldType(ht, isOptional = false, nameTransformer))
    else
      NotNullType(ListType(fieldType(ht, isOptional = false, nameTransformer)))

  def fieldType(
      ht: PType,
      isOptional: Boolean = false,
      nameTransformer: String => String = identity,
      transformAll: Boolean = false
  ): Type =
    ht match {
      case PArray(ht) =>
        if (isOptional)
          ListType(
            fieldType(ht, isOptional = false, nameTransformer, transformAll)
          )
        else
          NotNullType(
            ListType(
              fieldType(ht, isOptional = false, nameTransformer, transformAll)
            )
          )
      case PBool if transformAll =>
        if (isOptional) NamedType(nameTransformer("Boolean"))
        else NotNullType(NamedType(nameTransformer("Boolean")))
      case PBool =>
        if (isOptional) NamedType("Boolean")
        else NotNullType(NamedType("Boolean"))
      case PDate if transformAll =>
        if (isOptional) NamedType(nameTransformer("Date"))
        else NotNullType(NamedType(nameTransformer("Date")))
      case PDate =>
        if (isOptional) NamedType("Date") else NotNullType(NamedType("Date"))
      case PFloat if transformAll =>
        if (isOptional) NamedType(nameTransformer("Float"))
        else NotNullType(NamedType(nameTransformer("Float")))
      case PFloat =>
        if (isOptional) NamedType("Float") else NotNullType(NamedType("Float"))
      case PInt if transformAll =>
        if (isOptional) NamedType(nameTransformer("Int"))
        else NotNullType(NamedType(nameTransformer("Int")))
      case PInt =>
        if (isOptional) NamedType("Int") else NotNullType(NamedType("Int"))
      case PString if transformAll =>
        if (isOptional) NamedType(nameTransformer("String"))
        else NotNullType(NamedType(nameTransformer("String")))
      case PString =>
        if (isOptional) NamedType("String")
        else NotNullType(NamedType("String"))
      case PAny if transformAll =>
        if (isOptional) NamedType(nameTransformer("Any"))
        else NotNullType(NamedType(nameTransformer("Any")))
      case PAny =>
        if (isOptional) NamedType("Any")
        else NotNullType(NamedType("Any"))
      case POption(ht) =>
        fieldType(ht, isOptional = true, nameTransformer, transformAll)
      case PFile(_, _) if transformAll =>
        if (isOptional) NamedType(nameTransformer("String"))
        else NotNullType(NamedType(nameTransformer("String")))
      case PFile(_, _) =>
        if (isOptional) NamedType("String")
        else NotNullType(NamedType("String"))
      case s: PShape =>
        if (isOptional) NamedType(nameTransformer(s.id))
        else NotNullType(NamedType(nameTransformer(s.id)))
      case e: PEnum if transformAll =>
        if (isOptional) NamedType(nameTransformer(e.id))
        else NotNullType(NamedType(nameTransformer(e.id)))
      case e: PEnum =>
        if (isOptional) NamedType(e.id)
        else NotNullType(NamedType(e.id))
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

  def graphQlFieldArgs(args: Map[String, Type]) =
    args.map(arg => InputValueDefinition(arg._1, arg._2, None)).toVector

  def pshapeField(
      f: PShapeField
  ): FieldDefinition = FieldDefinition(
    name = f.id,
    fieldType = fieldType(f.ptype),
    arguments = f.ptype match {
      case PArray(ptype) =>
        graphQlFieldArgs(
          Map(
            "aggregation" -> gqlType(
              ptype,
              name => name + inputKindSuffix(AggInput),
              isOptional = true
            )
          )
        )
      case _ => Vector.empty
    }
  )

  lazy val buitlinGraphQlDefinitions =
    gql"""
    input IntAggInput {
      filter: [IntFilter!]
      orderBy: OrderByInput
      from: Int
      to: Int
    }

    input IntFilter {
      predicate: IntPredicate!
      and: [IntFilter!]
      or: [IntFilter!]
      negated: Boolean
    }

    input FloatAggInput {
      filter: [FloatFilter!]
      orderBy: OrderByInput
      from: Int
      to: Int
    }

    input FloatFilter {
      predicate: FloatPredicate!
      and: [FloatFilter!]
      or: [FloatFilter!]
      negated: Boolean
    }

    input StringAggInput {
      filter: [StringFilter!]
      orderBy: OrderByInput
      from: Int
      to: Int
    }

    input StringFilter {
      predicate: StringPredicate!
      and: [StringFilter!]
      or: [StringFilter!]
      negated: Boolean
    }

    input ArrayAggInput {
      filter: [ArrayFilter!]
      orderBy: OrderByInput
      from: Int
      to: Int
    }

    input ArrayFilter {
      predicate: ArrayPredicate!
      and: [ArrayFilter!]
      or: [ArrayFilter!]
      negated: Boolean
    }

    input BooleanAggInput {
      filter: [BooleanFilter!]
      orderBy: OrderByInput
      from: Int
      to: Int
    }

    input BooleanFilter {
      predicate: BooleanPredicate!
      and: [BooleanFilter!]
      or: [BooleanFilter!]
      negated: Boolean
    }
    input IntPredicate {
      lt: Int
      gt: Int
      eq: Int
      gte: Int
      lte: Int
    }

    input FloatPredicate {
      lt: Float
      gt: Float
      eq: Float
      gte: Float
      lte: Float
    }

    input StringPredicate {
      length: IntPredicate
      startsWith: String
      endsWith: String
      pattern: String
      eq: String
    }

    input ArrayPredicate {
      length: IntPredicate
    }

    input BooleanPredicate {
      eq: Boolean
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

    directive @listen(to: EventEnum!) on FIELD # on field selections inside a subscription
      """.definitions.toList
}
