package parsing
import org.parboiled2._
import domain._
import primitives._
import scala.collection.immutable.ListMap
import scala.language.implicitConversions

class HeavenlyParser(val input: ParserInput) extends Parser {

  def syntaxTree: Rule1[List[HConstruct]] = rule {
    whitespace() ~ zeroOrMore(importDef | modelDef | enumDef) ~ whitespace() ~>
      ((cs: Seq[HConstruct]) => cs.toList) ~ EOI
  }

  implicit def whitespace(terminal: String = ""): Rule0 = rule {
    zeroOrMore(anyOf(" \n\r\t")) ~
      str(terminal) ~
      zeroOrMore(anyOf(" \n\r\t"))
  }

  implicit def intToPos(i: Int) = Position(i, input)

  def identifier: Rule1[String] = rule {
    capture(predicate(CharPredicate.Alpha)) ~
      optional(capture(oneOrMore(CharPredicate.AlphaNum))) ~>
      ((init: String, rest: Option[String]) => init + rest.getOrElse(""))
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

  // Note: returns an array with unknown element type if the array is empty.
  def arrayVal: Rule1[HArrayValue] = rule {
    "[" ~ zeroOrMore(arrayItem()) ~ "]" ~>
      ((elements: Seq[HValue]) => {
        HArrayValue(elements.toList, elements.headOption match {
          case Some(v) => v.htype
          case _       => new HType {}
        })
      })
  }

  def literal: Rule1[HValue] = rule {
    floatVal | integerVal | stringVal | booleanVal | arrayVal
  }

  // Returns a PrimitiveType or an HModel with no fields or directives.
  def htypeFrom(typeId: String): HType = typeId match {
    case "String"  => HString
    case "Integer" => HInteger
    case "Float"   => HFloat
    case "Boolean" => HBool
    case "Date"    => HDate
    case "File"    => HFile(0, Nil)
    case id        => HReference(id)
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
    push(cursor) ~ directive ~ push(cursor) ~>
      ((start: Int, did: String, args: Option[HInterfaceValue], end: Int) => {
        ModelDirective(did, args match {
          case Some(args) => args
          case None       => HInterfaceValue(ListMap.empty, HInterface("", Nil, None))
        }, Some(PositionRange(start, end)))
      })
  }

  def fieldDirective: Rule1[FieldDirective] = rule {
    push(cursor) ~ directive ~ push(cursor) ~>
      ((start: Int, did: String, args: Option[HInterfaceValue], end: Int) => {
        FieldDirective(did, args match {
          case Some(args) => args
          case None       => HInterfaceValue(ListMap.empty, HInterface("", Nil, None))
        }, Some(PositionRange(start, end)))
      })
  }

  def modelDef: Rule1[HModel] = rule {
    whitespace() ~ zeroOrMore(modelDirective) ~
      ("model" ~ push(cursor) ~ identifier ~ push(cursor) ~
        "{" ~ zeroOrMore(fieldDef) ~ "}") ~>
      (
          (
              ds: Seq[ModelDirective],
              start: Int,
              id: String,
              end: Int,
              fields: Seq[HModelField]
          ) =>
            HModel(
              id,
              fields.toList,
              ds.toList,
              Some(PositionRange(start, end))
            )
        )
  }

  def defaultValue = rule { "=" ~ literal }

  def fieldDef: Rule1[HModelField] = rule {
    whitespace() ~ zeroOrMore(fieldDirective) ~
      push(cursor) ~ identifier ~ push(cursor) ~ ":" ~
      htype ~ optional(defaultValue) ~ optional(",") ~>
      (
          (
              ds: Seq[FieldDirective],
              start: Int,
              id: String,
              end: Int,
              ht: HType,
              dv: Option[HValue]
          ) => {
            val fieldIsOptional = ht match {
              case HOption(_) => true
              case _          => false
            }
            val defaultValue = dv.collect {
              case HArrayValue(Nil, _) =>
                HArrayValue(Nil, ht match {
                  case HArray(htype) => htype
                  case _             => new HType {}
                })
              case nonArray => nonArray
            }
            HModelField(
              id,
              ht,
              defaultValue,
              ds.toList,
              fieldIsOptional,
              Some(PositionRange(start, end))
            )
          }
      )
  }

  def enumDef: Rule1[HEnum] = rule {
    "enum" ~ push(cursor) ~ identifier ~ push(cursor) ~ "{" ~
      zeroOrMore(whitespace() ~ (identifier | stringVal) ~ whitespace())
        .separatedBy(optional(",")) ~ "}" ~>
      (
          (
              start: Int,
              id: String,
              end: Int,
              values: Seq[java.io.Serializable]
          ) => {
            val variants = values
              .map(_ match {
                case HStringValue(s) => s
                case s: String       => s
              })
              .toList
            HEnum(id, variants, Some(PositionRange(start, end)))
          }
      )
  }

  def importDef = rule {
    "import" ~ stringVal ~ "as" ~ push(cursor) ~ identifier ~ push(cursor) ~>
      (
          (
              file: HStringValue,
              start: Int,
              id: String,
              end: Int
          ) => {
            HImport(id, file.value, Some(PositionRange(start, end)))
          }
      )
  }

}
