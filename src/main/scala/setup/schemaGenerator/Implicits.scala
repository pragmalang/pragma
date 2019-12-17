package setup.schemaGenerator

import scala.language.implicitConversions
import domain._, primitives._
import sangria.ast.NamedType

package object Implicits {
  implicit def fromGraphQLNamedTypeToHType(namedType: NamedType): HType =
    namedType.name match {
      case "String"  => HString
      case "Int"     => HInteger
      case "Float"   => HFloat
      case "ID"      => HString
      case "Boolean" => HBool
      case "Any"     => HAny
      case name      => HReference(name)
    }
}
