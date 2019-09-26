package parsing
import org.parboiled2._
import domain._
import primitives._
import scala.collection.immutable.ListMap
import scala.language.implicitConversions

class HeavenlyParser(val input: ParserInput) extends Parser {

  def syntaxTree: Rule1[SyntaxTree] = rule {
    whitespace() ~ zeroOrMore(modelDef | enumDef) ~ whitespace() ~>
      ((cs: Seq[HConstruct]) => SyntaxTree(cs.toList)) ~ EOI
  }

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
    def escapedChar: Rule1[String] = rule {
      valueMap(
        Map(
          "\\t" -> "\t",
          "\\b" -> "\b",
          "\\r" -> "\r",
          "\\n" -> "\n",
          "\\\"" -> "\"",
          "\\\\" -> "\\"
        )
      ) | capture(noneOf("\"\\"))
    }
    rule {
      '"' ~ zeroOrMore(escapedChar) ~ '"' ~>
        ((s: Seq[String]) => HStringValue(s.mkString))
    }
  }

  def booleanVal: Rule1[HBoolValue] = rule {
    valueMap(Map("true" -> HBoolValue(true), "false" -> HBoolValue(false)))
  }

  val arrayItem = () => rule { literal ~ optional(",") }

  def arrayVal: Rule1[HArrayValue] = rule {
    "[" ~ zeroOrMore(arrayItem()) ~ "]" ~>
      ((elements: Seq[HValue]) => {
        HArrayValue(elements.toList, new HType {})
      })
  }

  def literal: Rule1[HValue] = rule {
    floatVal | integerVal | stringVal | booleanVal | arrayVal
  }

  /** Returns a PrimitiveType or an HModel with no fields or directives.
    * NOTE: File type is given size 0 and aRule1[HValuen empty list of extensions.
    */
  def htypeFrom(typeId: String): HType = typeId match {
    case "String"  => HString
    case "Integer" => HInteger
    case "Float"   => HFloat
    case "Boolean" => HBool
    case "Date"    => HDate
    case "File"    => HFile(0, Nil)
    case id        => HModel(id, Nil, Nil, None)
  }

  def htype: Rule1[HType] = rule {
    '[' ~ identifier ~ ']' ~> ((id: String) => HArray(htypeFrom(id))) |
      (identifier ~ '?') ~> ((id: String) => HOption(htypeFrom(id))) |
      identifier ~> ((id: String) => htypeFrom(id))
  }

  def namedArg = rule {
    identifier ~ ":" ~ literal ~> ((key: String, value: HValue) => key -> value)
  }

  def namedArgs: Rule1[HInterfaceValue] = rule {
    zeroOrMore(namedArg).separatedBy(",") ~>
      ((pairs: Seq[(String, HValue)]) => {
        HInterfaceValue(
          pairs.foldLeft(ListMap.empty[String, HValue])(_ + _),
          HInterface("", Nil, None)
        )
      })
  }

  def positionalArgs: Rule1[HInterfaceValue] = rule {
    zeroOrMore(literal).separatedBy(",") ~>
      ((args: Seq[HValue]) => {
        HInterfaceValue(
          args.zipWithIndex
            .map(pair => pair._2.toString -> pair._1)
            .foldLeft(ListMap.empty[String, HValue])(_ + _),
          HInterface("", Nil, None)
        )
      })
  }

  def arguments: Rule1[HInterfaceValue] = rule { namedArgs | positionalArgs }

  def directive = rule {
    '@' ~ identifier ~ optional("(" ~ arguments ~ ")") ~ whitespace()
  }

  def modelDirective: Rule1[ModelDirective] = rule {
    directive ~>
      ((did: String, args: Option[HInterfaceValue]) => {
        ModelDirective(did, args match {
          case Some(args) => args
          case None       => HInterfaceValue(ListMap.empty, HInterface("", Nil, None))
        }, None)
      })
  }

  def fieldDirective: Rule1[FieldDirective] = rule {
    directive ~>
      ((did: String, args: Option[HInterfaceValue]) => {
        FieldDirective(did, args match {
          case Some(args) => args
          case None       => HInterfaceValue(ListMap.empty, HInterface("", Nil, None))
        }, None)
      })
  }

  def modelDef: Rule1[HModel] = rule {
    whitespace() ~ zeroOrMore(modelDirective) ~
      ("model" ~ identifier ~ "{" ~ zeroOrMore(fieldDef) ~ "}") ~>
      ((ds: Seq[ModelDirective], id: String, fields: Seq[HModelField]) => {
        HModel(id, fields.toList, ds.toList, None)
      })
  }

  def defaultValue = rule { "=" ~ literal }

  def fieldDef: Rule1[HModelField] = rule {
    whitespace() ~ zeroOrMore(fieldDirective) ~ identifier ~ ":" ~
      htype ~ optional(defaultValue) ~ optional(",") ~>
      ((ds: Seq[FieldDirective], id: String, ht: HType, dv: Option[HValue]) => {
        HModelField(id, ht, dv, ds.toList, ht match {
          case HOption(_) => true
          case _          => false
        }, None)
      })
  }

  def enumDef: Rule1[HEnum] = rule {
    "enum" ~ identifier ~ "{" ~
      zeroOrMore(whitespace() ~ (identifier | stringVal) ~ whitespace())
        .separatedBy(optional(",")) ~ "}" ~>
      ((id: String, values: Seq[java.io.Serializable]) => {
        val variants = values
          .map(_ match {
            case HStringValue(s) => s
            case s: String       => s
          })
          .toList
        HEnum(id, variants, None)
      })
  }

}
