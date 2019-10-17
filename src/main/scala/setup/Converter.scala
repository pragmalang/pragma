package setup

import domain._
import primitives._
import utils._

import sangria.validation.ValueCoercionViolation
import sangria.marshalling.DateSupport
import sangria.ast._
import sangria.schema.{ObjectType, Schema}

trait Converter extends WithSyntaxTree {
  type Def
  type EnumDef
  type ShapeDef
  type FieldTypeDef
  type FieldDef

  override val syntaxTree: SyntaxTree

  def typeDefinitions(): Vector[Def]
  def fieldType(ht: HType, isOptional: Boolean = false): FieldTypeDef
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

  override def fieldType(ht: HType, isOptional: Boolean = false): Type =
    ht match {
      case HArray(ht) =>
        if (isOptional) ListType(fieldType(ht))
        else NotNullType(ListType(fieldType(ht)))
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
        if (isOptional) NamedType(s.id) else NotNullType(NamedType(s.id))
      case e: HEnum =>
        if (isOptional) NamedType(e.id) else NotNullType(NamedType(e.id))
      case HReference(id) =>
        if (isOptional) NamedType(id) else NotNullType(NamedType(id))
      case HSelf(id) =>
        if (isOptional) NamedType(id) else NotNullType(NamedType(id))
      case HFunction(args, returnType) =>
        throw new Exception("Function can't be used as a field type")
    }

  override def hEnum(e: HEnum): EnumTypeDefinition =
    EnumTypeDefinition(
      name = e.id,
      values = e.values.map(v => EnumValueDefinition(name = v)).toVector
    )

  override def hShape(s: HShape): ObjectTypeDefinition = s match {
    case HModel(id, fields, _, _) =>
      ObjectTypeDefinition(
        id,
        Vector.empty,
        fields.map(hShapeField).toVector
      )
    case HInterface(id, fields, _) =>
      ObjectTypeDefinition(
        id,
        Vector.empty,
        fields.map(hShapeField).toVector
      )
  }

  override def hShapeField(f: HShapeField): FieldDefinition = f match {
    case HModelField(id, htype, _, _, _) =>
      FieldDefinition(
        name = id,
        fieldType = fieldType(htype),
        Vector.empty
      )
    case HInterfaceField(id, htype, _) =>
      FieldDefinition(
        name = id,
        fieldType = fieldType(htype),
        Vector.empty
      )
  }
}
