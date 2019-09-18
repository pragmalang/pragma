package parsing
import org.parboiled2._
import domain._
import domain.primitives._

class HeavenlyParser(val input: ParserInput) extends Parser {
  implicit def whitespace(terminal: String = ""): Rule0 = rule {
    zeroOrMore(ch(' ') | '\n' | '\r' | '\r') ~
      str(terminal) ~
      zeroOrMore(ch(' ') | '\n' | '\r' | '\r')
  }

  def identifier: Rule1[String] = rule {
    capture(predicate(CharPredicate.Alpha)) ~
      capture(oneOrMore(CharPredicate.AlphaNum)) ~>
      ((init: String, rest: String) => init + rest)
  }

  /**
    * Returns a PrimitiveType or an HModel with no fields or directives.
    * NOTE: File type is given size 0 and an empty list of extensions.
    */
  def htypeFrom(typeId: String): HType = typeId match {
    case "String"  => HString
    case "Integer" => HInteger
    case "Float"   => HFloat
    case "Boolean" => HBool
    case "Date"    => HDate
    case "File"    => HFile(0, Nil)
    case id        => HModel(id, Nil, Nil)
  }

  def htype: Rule1[HType] = rule {
    '[' ~ identifier ~ ']' ~> ((id: String) => HArray(htypeFrom(id))) |
      (identifier ~ '?') ~> ((id: String) => HOption(htypeFrom(id))) |
      identifier ~> ((id: String) => htypeFrom(id))
  }

  def modelDef: Rule1[HModel] = rule {
    ("model" ~ identifier ~ "{" ~ zeroOrMore(fieldDef) ~ "}") ~> (
        (
            id: String,
            fields: Seq[HModelField]
        ) => HModel(id, fields.toList, Nil)
    )
  }

  def fieldDef: Rule1[HModelField] = rule {
    whitespace() ~ identifier ~ ":" ~ htype ~ optional(",") ~> (
        (
            id: String,
            ht: HType
        ) =>
          HModelField(id, ht, Nil, ht match {
            case HOption(_) => true
            case _          => false
          })
      )
  }

}
