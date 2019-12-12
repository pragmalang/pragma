package parsing
import org.parboiled2._
import domain._
import primitives._
import scala.collection.immutable.ListMap
import scala.language.implicitConversions
import domain.utils.`package`.Identifiable
import running.pipeline.PipelineInput
import running.pipeline.PipelineOutput
import shapeless._

object HeavenlyParser {
  type ExternalFunction = GraalFunction
  // Dummy classes will be substituted at substitution
  // Dummy placeholder expression
  case class Reference(
      id: String,
      child: Option[Reference],
      position: Option[PositionRange]
  ) extends Identifiable
      with Positioned
      with HFunctionValue[PipelineInput, PipelineOutput] {
    override def execute(input: PipelineInput): PipelineOutput =
      throw new Exception(
        "Reference should not be executed before substitution"
      )
    override val htype: HFunction =
      HFunction(ListMap.empty, new HType {})
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
    val htype = HReference(modelId + "." + childId)
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
    '@' ~ identifier ~ optional("(" ~ arguments ~ ")")
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
    whitespace() ~ zeroOrMore(modelDirective).separatedBy(whitespace()) ~
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
    whitespace() ~ zeroOrMore(fieldDirective).separatedBy(whitespace()) ~
      whitespace() ~ push(cursor) ~ identifier ~ push(cursor) ~ ":" ~
      htype ~ optional(defaultValue) ~
      optional(
        oneOrMore(anyOf(" \r\t")) ~
          zeroOrMore(fieldDirective).separatedBy(oneOrMore(anyOf(" \r\t")))
      ) ~ optional(",") ~> {
      (
          ds: Seq[FieldDirective],
          start: Int,
          id: String,
          end: Int,
          ht: HType,
          dv: Option[HValue],
          trailingDirectives: Option[Seq[FieldDirective]]
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
            ds.concat(trailingDirectives.getOrElse(Nil)).toList,
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

  def singletonEvent: Rule1[List[HEvent]] = rule {
    oneOrMore(event) ~> ((events: Seq[HEvent]) => events.toList)
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

  def ref: Rule1[Reference] = rule {
    push(cursor) ~ identifier ~ push(cursor) ~
      optional("." ~ ref) ~ push(cursor) ~> {
      (
          start: Int,
          parent: String,
          parentEnd: Int,
          child: Option[Reference],
          end: Int
      ) =>
        Reference(parent, child, Some(PositionRange(start, end)))
    }
  }

  def accessRule: Rule1[AccessRule] = rule {
    push(cursor) ~ whitespace() ~
      valueMap(Map("allow" -> Allow, "deny" -> Deny)) ~
      whitespace() ~ (singletonEvent | eventsList | allEvents) ~ whitespace() ~
      (modelResource | fieldResource ~> (_.asInstanceOf[Resource])) ~
      whitespace() ~ ref ~ push(cursor) ~> {
      (
          start: Int,
          ruleKind: RuleKind,
          events: List[HEvent],
          resource: Resource,
          authorizor: Reference,
          end: Int
      ) =>
        AccessRule(
          ruleKind,
          resource,
          events,
          authorizor,
          Some(PositionRange(start, end))
        )
    }
  }

  def role: Rule1[Role] = rule {
    "role" ~ push(cursor) ~ identifier ~ push(cursor) ~ "{" ~
      zeroOrMore(accessRule) ~ "}" ~> {
      (start: Int, roleName: String, end: Int, rules: Seq[AccessRule]) =>
        Role(HReference(roleName), rules.toList)
    }
  }

  def acl: Rule1[Permissions] = rule {
    "acl" ~ "{" ~
      push(cursor) ~ zeroOrMore(role | accessRule) ~ push(cursor) ~
      "}" ~> { (start: Int, rulesAndRoles: Seq[Product], end: Int) =>
      Permissions(
        Tenant(
          "root",
          rulesAndRoles.collect { case rule: AccessRule => rule }.toList,
          rulesAndRoles.collect { case role: Role       => role }.toList,
          None
        ),
        Nil,
        Some(PositionRange(start, end))
      )
    }
  }

}
