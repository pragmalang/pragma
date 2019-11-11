package parsing
import org.parboiled2._
import domain._
import primitives._
import scala.collection.immutable.ListMap
import scala.language.implicitConversions

object HeavenlyParser {
  // Dummy classes will be substituted at validation
  // Dummy placeholder expression
  case class ReferenceExpression(
      parent: String,
      position: Option[PositionRange]
  ) extends HExpression {
    override def eval(context: HObject): HValue =
      throw new Exception(
        s"Reference expression `$parent` should not be evaluated before validation and substiturion."
      )
  }
  // Dummy placeholder resource
  case class ResourceReference(
      resourceId: String,
      fieldId: Option[String],
      position: Option[PositionRange]
  ) extends HShape {
    val fields: List[HShapeField] = Nil
    val id = resourceId
  }
  case class FieldReference(
      modelId: String,
      childId: String,
      position: Option[PositionRange]
  ) extends HShapeField {
    val htype = new HType {}
    val id = "DUMMY FIELD"
  }
}
class HeavenlyParser(val input: ParserInput) extends Parser {
  import parsing.HeavenlyParser._

  def syntaxTree: Rule1[List[HConstruct]] = rule {
    whitespace() ~ zeroOrMore(importDef | modelDef | enumDef | configDef) ~ whitespace() ~>
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

  def memberExpr: Rule1[MemberExpression] = rule {
    push(cursor) ~ identifier ~ push(cursor) ~ "." ~ identifier ~ push(cursor) ~> {
      (start: Int, parent: String, parentEnd: Int, child: String, end: Int) =>
        MemberExpression(
          ReferenceExpression(parent, Some(PositionRange(start, parentEnd))),
          child,
          Some(PositionRange(start, end))
        )
    }
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
        "{" ~ zeroOrMore(fieldDef) ~ "}") ~> {
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
    }
  }

  def defaultValue = rule { "=" ~ literal }

  def fieldDef: Rule1[HModelField] = rule {
    whitespace() ~ zeroOrMore(fieldDirective) ~
      push(cursor) ~ identifier ~ push(cursor) ~ ":" ~
      htype ~ optional(defaultValue) ~ optional(",") ~> {
      (
          ds: Seq[FieldDirective],
          start: Int,
          id: String,
          end: Int,
          ht: HType,
          dv: Option[HValue]
      ) =>
        {
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
            Some(PositionRange(start, end))
          )
        }
    }
  }

  def enumDef: Rule1[HEnum] = rule {
    "enum" ~ push(cursor) ~ identifier ~ push(cursor) ~ "{" ~
      zeroOrMore(whitespace() ~ (identifier | stringVal) ~ whitespace())
        .separatedBy(optional(",")) ~ "}" ~> {
      (
          start: Int,
          id: String,
          end: Int,
          values: Seq[java.io.Serializable]
      ) =>
        {
          val variants = values
            .map(_ match {
              case HStringValue(s) => s
              case s: String       => s
            })
            .toList
          HEnum(id, variants, Some(PositionRange(start, end)))
        }
    }
  }

  def importDef: Rule1[HImport] = rule {
    "import" ~ stringVal ~ "as" ~ push(cursor) ~ identifier ~ push(cursor) ~> {
      (
          file: HStringValue,
          start: Int,
          id: String,
          end: Int
      ) =>
        {
          HImport(id, file.value, Some(PositionRange(start, end)))
        }
    }
  }

  def configDef: Rule1[HConfig] = rule {
    push(cursor) ~ "config" ~ push(cursor) ~ "{" ~ zeroOrMore(configEntry) ~ "}" ~>
      ((start: Int, end: Int, entries: Seq[ConfigEntry]) => {
        HConfig(entries.toList, Some(PositionRange(start, end)))
      })
  }

  def configEntry: Rule1[ConfigEntry] = rule {
    push(cursor) ~ identifier ~ push(cursor) ~ "=" ~
      literal ~ optional(",") ~ whitespace() ~>
      ((start: Int, key: String, end: Int, value: HValue) => {
        ConfigEntry(key, value, Some(PositionRange(start, end)))
      })
  }

  def event: Rule1[HEvent] = rule {
    valueMap(
      Map(
        "CREATE" -> Create,
        "READ" -> Read,
        "UPDATE" -> Update,
        "DELETE" -> Delete
      )
    )
  }

  def allEvents: Rule1[List[HEvent]] = rule {
    "ALL" ~ push(List(Create, Read, Update, Delete))
  }

  def eventsList: Rule1[List[HEvent]] = rule {
    ("[" ~ oneOrMore(event).separatedBy(",") ~ "]") ~>
      ((events: Seq[HEvent]) => events.toList) | allEvents
  }

  def modelResource: Rule1[ShapeResource] = rule {
    push(cursor) ~ identifier ~ push(cursor) ~> {
      (
          start: Int,
          id: String,
          end: Int
      ) =>
        ShapeResource(
          ResourceReference(id, None, Some(PositionRange(start, end)))
        )
    }
  }

  def fieldResource: Rule1[FieldResource] = rule {
    push(cursor) ~ identifier ~ "." ~ identifier ~ push(cursor) ~> {
      (start: Int, modelId: String, childId: String, end: Int) =>
        FieldResource(
          FieldReference(modelId, childId, Some(PositionRange(start, end))),
          new HShape {
            val id = "DUMMY SHAPE"
            val fields = Nil
            val position = None
          }
        )
    }
  }

  // def accessRule: Rule1[AccessRule] = rule {
  //   eventsList ~ whitespace(" ") ~ resource ~ whitespace(" ") ~ memberExpr ~> {
  //     (events: List[HEvent] , resource: Resource, authorizor: MemberExpression) =>

  //   }
  // }

  def permitDef = rule {
    "permit" ~ "{" ~ zeroOrMore("") ~ "}"
  }

}
