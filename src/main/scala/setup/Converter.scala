package setup

import domain._, primitives._, utils._

import sangria.ast._
import sangria.ast.{Directive => GraphQlDirective}
import sangria.macros._

trait Converter {
  type Def
  type EnumDef
  type ShapeDef
  type FieldTypeDef
  type FieldDef
  type DirectiveDef

  val syntaxTree: SyntaxTree

  def typeDefinitions(): List[Def]
  def fieldType(
      ht: HType,
      isOptional: Boolean,
      nameTransformer: String => String
  ): FieldTypeDef
  def hEnum(e: HEnum): EnumDef
  def hShape(s: HShape, directives: Vector[DirectiveDef]): ShapeDef
  def hShapeField(f: HShapeField): FieldDef

}

class GraphQlConverter(override val syntaxTree: SyntaxTree) extends Converter {

  override type Def = Definition
  override type EnumDef = EnumTypeDefinition
  override type ShapeDef = ObjectTypeDefinition
  override type FieldTypeDef = Type
  override type FieldDef = FieldDefinition
  override type DirectiveDef = GraphQlDirective

  def buildGraphQLAst() = Document(typeDefinitions.toVector)

  override def typeDefinitions(): List[Definition] =
    syntaxTree.models.map(hShape(_)) ++ syntaxTree.enums.map(hEnum(_))

  override def fieldType(
      ht: HType,
      isOptional: Boolean = false,
      nameTransformer: String => String = identity
  ): Type =
    ht match {
      case HArray(ht) =>
        if (isOptional)
          ListType(fieldType(ht, isOptional = true, nameTransformer))
        else
          NotNullType(
            ListType(fieldType(ht, isOptional = true, nameTransformer))
          )
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
      case HOption(ht) => fieldType(ht, isOptional = true, nameTransformer)
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

  override def hShape(
      s: HShape,
      directives: Vector[GraphQlDirective] = Vector.empty
  ): ObjectTypeDefinition = ObjectTypeDefinition(
    s.id,
    Vector.empty,
    s.fields
      .filter({
        case field: HInterfaceField => true
        case field: HModelField =>
          !field.directives.exists(
            directive => directive.id == "secretCredential"
          )
      })
      .map(hShapeField)
      .toVector,
    directives
  )

  override def hShapeField(
      f: HShapeField
  ): FieldDefinition = FieldDefinition(
    name = f.id,
    fieldType = fieldType(f.htype),
    Vector.empty
  )
}

object GraphQlConverter {
  def apply(syntaxTree: SyntaxTree) = new GraphQlConverter(syntaxTree)
}
