package setup
import domain._
import TSType._

class TSTypesGen(syntaxTree: SyntaxTree) {
  val tsTypes = (syntaxTree.models ++ syntaxTree.enums).map(tsType).toList
  private def tsType(ptype: PType): TSType = ptype match {
    case PReference(id) => tsType(syntaxTree.modelsById(id))
    case PModel(id, fields, _, _, _) =>
      TSInterface(id, fields.toList.map(field))
    case PInterface(id, fields, _) => TSInterface(id, fields.toList.map(field))
    case PEnum(id, values, _)      => TSEnum(id, values.toList)
    case arr: PArray               => TSArray(tsType(arr.ptype))
    case PString                   => TSString
    case PInt                      => TSNumber
    case PFloat                    => TSNumber
    case PBool                     => TSBoolean
    case PDate                     => TSDate
    case PFile(_, _)               => TSString
    case PFunction(_, t)           => tsType(t)
    case POption(t)                => tsType(t)
    case PAny                      => TSAny
  }
  private def field(f: PShapeField): TSInterfaceField = f.ptype match {
    case POption(ptype) =>
      TSInterfaceField(f.id, tsType(ptype), true)
    case ptype => TSInterfaceField(f.id, tsType(ptype), false)
  }
  val renderTypes = tsTypes.map(_.render()).mkString("\n\n")
}

sealed trait TSType {
  def render(verbose: Boolean = true): String = this match {
    case TSInterface(name, _) if !verbose => name
    case TSEnum(name, _) if !verbose      => name
    case TSInterface(name, fields) if verbose =>
      s"""|interface $name {
          |${fields.map(f => "  " + f.render).mkString("\n")}
          |}
          |""".stripMargin
    case TSEnum(name, variants) if verbose =>
      s"""|enum $name {
          |${variants.map("  " + _).mkString(",\n")}
          |}
          |""".stripMargin
    case TSArray(of) => s"Array<${of.render(verbose = false)}>"
    case TSNumber    => "number"
    case TSBoolean   => "boolean"
    case TSString    => "string"
    case TSDate      => "Date"
    case TSAny       => "Any"
  }

}
object TSType {
  case class TSInterface(name: String, fields: List[TSInterfaceField])
      extends TSType
  case class TSArray(of: TSType) extends TSType
  case class TSEnum(name: String, variants: List[String]) extends TSType
  case object TSNumber extends TSType
  case object TSBoolean extends TSType
  case object TSString extends TSType
  case object TSDate extends TSType
  case object TSAny extends TSType
}

case class TSInterfaceField(name: String, tsType: TSType, isOptional: Boolean) {
  private val opt: String = if (isOptional) "?" else ""
  val render: String = s"$name$opt: ${tsType.render(verbose = false)};"
}
