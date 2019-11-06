package setup

import domain._
import primitives._
import utils._

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

  def typeDefinitions(): Vector[Def]
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

  override type Def = Definition
  override type EnumDef = EnumTypeDefinition
  override type ShapeDef = ObjectTypeDefinition
  override type FieldTypeDef = Type
  override type FieldDef = FieldDefinition

  def buildGraphQLAst() = Document(typeDefinitions)

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
        case None => Document(typeDefinitions :+ query)
        case Some(subscription) =>
          Document(typeDefinitions :+ query :+ subscription)
      }
    case Some(mutation) =>
      subscription match {
        case None => Document(typeDefinitions :+ query :+ mutation)
        case Some(subscription) =>
          Document(typeDefinitions :+ query :+ mutation :+ subscription)
      }
  }

  override def typeDefinitions(): Vector[Definition] =
    (syntaxTree.models.map(hShape(_)) ++ syntaxTree.enums.map(hEnum(_))).toVector

  override def fieldType(
      ht: HType,
      isOptional: Boolean = false,
      nameTransformer: String => String = name => name
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
      nameTransformer: String => String,
      args: Map[String, Type],
      fieldType: Type
  )(modelId: String) = FieldDefinition(
    name = nameTransformer(modelId),
    fieldType = fieldType,
    arguments = graphQlFieldArgs(args)
  )

  def outputTypes: Vector[Definition] = typeDefinitions map {
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

  def inputTypes(kind: GraphQlConverter.InputKind): Vector[Definition] =
    syntaxTree.models.toVector.map { model =>
      InputObjectTypeDefinition(
        name = model.id.capitalize + GraphQlConverter.inputKindSuffix(kind),
        fields = model.fields.toVector.map { field =>
          InputValueDefinition(
            name = field.id,
            valueType = fieldType(
              ht = field.htype,
              nameTransformer = name =>
                name.capitalize + GraphQlConverter.inputKindSuffix(
                  GraphQlConverter.ReferenceInput
                ),
              isOptional = kind match {
                case GraphQlConverter.ObjectInput   => false
                case GraphQlConverter.OptionalInput => true
                case GraphQlConverter.ReferenceInput =>
                  !field.directives.exists(fd => fd.id == "primary")
              }
            ),
            None
          )
        }
      )
    }

  def notificationTypes: Vector[Definition] =
    syntaxTree.models.toVector.map(
      model =>
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
              fieldType = NotNullType(NamedType(model.id)),
              arguments = Vector.empty
            )
          )
        )
    )
}

object GraphQlConverter {
  sealed trait InputKind
  object ObjectInput extends InputKind
  object ReferenceInput extends InputKind
  object OptionalInput extends InputKind

  def inputKindSuffix(kind: GraphQlConverter.InputKind) = kind match {
    case GraphQlConverter.ObjectInput    => "ObjectInput"
    case GraphQlConverter.OptionalInput  => "OptionalInput"
    case GraphQlConverter.ReferenceInput => "ReferenceInput"
  }

  def buitlinGraphQlTypeDefinitions =
    gql"""
  input EqInput {
    field: String!
    value: Any!
  }
  
  input WhereInput {
    predicate: PredicateInput
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

  input LogicalPredicateInput {
    AND: [LogicalPredicateInput]
    OR: [LogicalPredicateInput]
    predicate: PredicateInput
  }
  
  input PredicateInput {
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
  """.definitions
}
