package setup

import domain._
import primitives._
import utils._

import sangria.validation.ValueCoercionViolation
import sangria.marshalling.DateSupport
import sangria.ast._
import sangria.schema.{ObjectType, Schema}

import com.github.nscala_time.time.Imports._
import org.joda.time.format.ISODateTimeFormat
import scala.util.{Success, Failure, Try}

case class GraphQlDefinitionsIR(syntaxTree: SyntaxTree) {

  def buildGraphQLAst() = Document(typeDefinitions)

  def buildGraphQLSchemaAst(
      query: ObjectTypeDefinition,
      mutation: Option[ObjectTypeDefinition],
      subscription: Option[ObjectTypeDefinition]
  ) = mutation match {
    case None => subscription match {
      case None => Document(typeDefinitions :+ query)
      case Some(subscription) =>  Document(typeDefinitions :+ query :+ subscription)
    }
    case Some(mutation) => subscription match {
      case None => Document(typeDefinitions :+ query :+ mutation)
      case Some(subscription) => Document(typeDefinitions :+ query :+ mutation :+ subscription)
    } 
  }

  def typeDefinitions(): Vector[Definition] =
    (syntaxTree.models.map(hShape(_)) ++ syntaxTree.enums.map(hEnum(_))).toVector

  def hType(ht: HType, isOptional: Boolean = false): Type = ht match {
    case HArray(ht) =>
      if (isOptional) ListType(hType(ht)) else NotNullType(ListType(hType(ht)))
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
      if (isOptional) NamedType("String") else NotNullType(NamedType("String"))
    case HOption(ht) => hType(ht, isOptional = true)
    case HFile(_, _) =>
      if (isOptional) NamedType("String") else NotNullType(NamedType("String"))
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

  def hEnum(e: HEnum): EnumTypeDefinition =
    EnumTypeDefinition(
      name = e.id,
      values = e.values.map(v => EnumValueDefinition(name = v)).toVector
    )

  def hShape(s: HShape): ObjectTypeDefinition = s match {
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

  def hShapeField(f: HShapeField): FieldDefinition = f match {
    case HModelField(id, htype, _, _, _) =>
      FieldDefinition(
        name = id,
        fieldType = hType(htype),
        Vector.empty
      )
    case HInterfaceField(id, htype, _) =>
      FieldDefinition(
        name = id,
        fieldType = hType(htype),
        Vector.empty
      )
  }
}
