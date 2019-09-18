package parsing
import org.parboiled2._
import domain._
import domain.primitives._

class HeavenlyParser(val input: ParserInput) extends Parser {
  implicit def whitespace(terminal: String = ""): Rule0 = rule {
    zeroOrMore(anyOf(" \n\r\t")) ~
      str(terminal) ~
      zeroOrMore(anyOf(" \n\r\t"))
  }

  def identifier: Rule1[String] = rule {
    capture(predicate(CharPredicate.Alpha)) ~
      capture(oneOrMore(CharPredicate.AlphaNum)) ~>
      ((init: String, rest: String) => init + rest)
  }

  def integerVal: Rule1[HIntegerValue] = rule {
    capture(oneOrMore(CharPredicate.Digit)) ~>
      ((int: String) => HIntegerValue(int.toLong))
  }

  def floatVal: Rule1[HFloatValue] = rule {
    capture(oneOrMore(CharPredicate.Digit)) ~ '.' ~
      capture(oneOrMore(CharPredicate.Digit)) ~>
      ((whole: String, fraction: String) => {
        HFloatValue((whole + '.' + fraction).toDouble)
      })
  }

  def stringVal: Rule1[HStringValue] = {
    val quote = "\""
    val escapedQuote = "\\\""
    rule {
      '"' ~ capture(zeroOrMore(escapedQuote | noneOf(quote))) ~ '"' ~>
        ((s: String) => HStringValue(s.replace(escapedQuote, quote)))
    }
  }

  def booleanVal: Rule1[HBoolValue] = rule {
    valueMap(Map("true" -> HBoolValue(true), "false" -> HBoolValue(false)))
  }

  val arrayItem = () => rule { literal ~ optional(",") }

  def arrayVal: Rule1[HArrayValue[HValue]] = rule {
    "[" ~ zeroOrMore(arrayItem()) ~ "]" ~>
      ((elements: Seq[HValue]) => {
        HArrayValue[HValue](elements.toList, new HType {})
      })
  }

  def literal: Rule1[HValue] = rule {
    floatVal | integerVal | stringVal | booleanVal | arrayVal
  }

  /** Returns a PrimitiveType or an HModel with no fields or directives.
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

  /** Parses models without directives. */
  def modelDef: Rule1[HModel] = rule {
    ("model" ~ identifier ~ "{" ~ zeroOrMore(fieldDef) ~ "}") ~>
      ((id: String, fields: Seq[HModelField]) => {
        HModel(id, fields.toList, Nil)
      })
  }

  /** Parses fields without directives. */
  def fieldDef: Rule1[HModelField] = rule {
    whitespace() ~ identifier ~ ":" ~ htype ~ optional(",") ~>
      ((id: String, ht: HType) => {
        HModelField(id, ht, Nil, ht match {
          case HOption(_) => true
          case _          => false
        })
      })
  }

}
