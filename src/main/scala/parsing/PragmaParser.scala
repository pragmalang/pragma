package parsing

import org.parboiled2._
import domain._
import primitives._
import scala.collection.immutable.ListMap
import scala.language.implicitConversions
import domain.utils.`package`.Identifiable
import spray.json.JsValue
import scala.util.{Try, Failure}

object PragmaParser {
  // Dummy classes will be substituted at substitution time
  case class Reference(
      path: List[String],
      position: Option[PositionRange] = None
  ) extends Identifiable
      with Positioned
      with PFunctionValue[JsValue, Try[JsValue]]
      with PShape
      with PShapeField {
    override val id = toString

    override def execute(input: JsValue) =
      Failure(
        throw new Exception(
          "Reference should not be executed before substitution"
        )
      )

    override val ptype: PFunction =
      PFunction(ListMap.empty, PAny)

    override def toString: String =
      path.head + path.tail.foldLeft("")(_ + "." + _)

    override val fields: List[PShapeField] = Nil

  }

}
class PragmaParser(val input: ParserInput) extends Parser {
  import parsing.PragmaParser._

  def syntaxTree: Rule1[List[PConstruct]] = rule {
    whitespace() ~
      zeroOrMore(
        importDef | modelDef | enumDef | configDef | accessRuleDef | roleDef
      ).separatedBy(whitespace()) ~ whitespace() ~>
      ((cs: Seq[PConstruct]) => cs.toList) ~ EOI
  }

  implicit def whitespace(terminal: String = ""): Rule0 = rule {
    zeroOrMore(anyOf(" \n\r\t")) ~
      str(terminal) ~
      zeroOrMore(anyOf(" \n\r\t"))
  }

  implicit def intToPos(i: Int) = Position(i, input)

  // An identifier starts with a non-underscore alphabetic character
  def identifier: Rule1[String] = rule {
    capture(predicate(CharPredicate.Alpha)) ~
      capture(zeroOrMore(CharPredicate.AlphaNum | '_')) ~>
      ((head: String, rest: String) => head + rest)
  }

  def integerVal: Rule1[PIntValue] = rule {
    capture(oneOrMore(CharPredicate.Digit)) ~>
      ((int: String) => PIntValue(int.toLong))
  }

  def floatVal: Rule1[PFloatValue] = rule {
    capture(oneOrMore(CharPredicate.Digit)) ~ '.' ~
      capture(oneOrMore(CharPredicate.Digit)) ~> {
      (whole: String, fraction: String) =>
        PFloatValue((whole + '.' + fraction).toDouble)
    }
  }

  def stringVal: Rule1[PStringValue] = {
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
        ((s: Seq[String]) => PStringValue(s.mkString))
    }
  }

  def booleanVal: Rule1[PBoolValue] = rule {
    valueMap(Map("true" -> PBoolValue(true), "false" -> PBoolValue(false)))
  }

  val arrayItem = () => rule { literal ~ optional(",") }

  // Note: returns an array with unknown element type if the array is empty.
  def arrayVal: Rule1[PArrayValue] = rule {
    "[" ~ zeroOrMore(arrayItem()) ~ "]" ~>
      ((elements: Seq[PValue]) => {
        PArrayValue(elements.toList, elements.headOption match {
          case Some(v) => v.ptype
          case _       => PAny
        })
      })
  }

  def literal: Rule1[PValue] = rule {
    floatVal | integerVal | stringVal | booleanVal | arrayVal
  }

  // Returns a PrimitiveType or an HModel with no fields or directives.
  def ptypeFrom(typeId: String): PType = typeId match {
    case "String"  => PString
    case "Int"     => PInt
    case "Float"   => PFloat
    case "Boolean" => PBool
    case "Date"    => PDate
    case "File"    => PFile(0, Nil)
    case id        => PReference(id)
  }

  def ptype: Rule1[PType] = rule {
    '[' ~ identifier ~ ']' ~> ((id: String) => PArray(ptypeFrom(id))) |
      (identifier ~ '?') ~> ((id: String) => POption(ptypeFrom(id))) |
      identifier ~> ((id: String) => ptypeFrom(id))
  }

  def namedArg = rule {
    identifier ~ ":" ~ (literal | ref) ~>
      ((key: String, value: PValue) => key -> value)
  }

  def namedArgs: Rule1[PInterfaceValue] = rule {
    zeroOrMore(namedArg).separatedBy(",") ~> { (pairs: Seq[(String, PValue)]) =>
      PInterfaceValue(ListMap.from(pairs), PInterface("", Nil, None))
    }
  }

  def positionalArgs: Rule1[PInterfaceValue] = rule {
    zeroOrMore(literal | ref).separatedBy(",") ~> { (args: Seq[PValue]) =>
      PInterfaceValue(
        args.zipWithIndex
          .map(pair => pair._2.toString -> pair._1)
          .foldLeft(ListMap.empty[String, PValue])(_ + _),
        PInterface("", Nil, None)
      )
    }
  }

  def arguments: Rule1[PInterfaceValue] = rule { namedArgs | positionalArgs }

  def directive = rule {
    '@' ~ identifier ~ optional("(" ~ arguments ~ ")")
  }

  def directive(dirKind: DirectiveKind): Rule1[Directive] = rule {
    push(cursor) ~ directive ~ push(cursor) ~> {
      (start: Int, did: String, args: Option[PInterfaceValue], end: Int) =>
        Directive(did, args match {
          case Some(args) => args
          case None       => PInterfaceValue(ListMap.empty, PInterface("", Nil, None))
        }, dirKind, Some(PositionRange(start, end)))
    }
  }

  def modelDef: Rule1[PModel] = rule {
    whitespace() ~ zeroOrMore(directive(ModelDirective))
      .separatedBy(whitespace()) ~
      ("model" ~ push(cursor) ~ identifier ~ push(cursor) ~
        "{" ~ zeroOrMore(fieldDef) ~ "}") ~> {
      (
          ds: Seq[Directive],
          start: Int,
          id: String,
          end: Int,
          fields: Seq[PModelField]
      ) =>
        PModel(
          id,
          fields.toList,
          ds.toList,
          Some(PositionRange(start, end))
        )
    }
  }

  def defaultValue = rule { "=" ~ literal }

  def fieldDef: Rule1[PModelField] = rule {
    whitespace() ~ zeroOrMore(directive(FieldDirective))
      .separatedBy(whitespace()) ~
      whitespace() ~ push(cursor) ~ identifier ~ push(cursor) ~ ":" ~
      ptype ~ optional(defaultValue) ~
      optional(
        oneOrMore(anyOf(" \r\t")) ~
          zeroOrMore(directive(FieldDirective))
            .separatedBy(oneOrMore(anyOf(" \r\t")))
      ) ~ optional(",") ~> {
      (
          ds: Seq[Directive],
          start: Int,
          id: String,
          end: Int,
          ht: PType,
          dv: Option[PValue],
          trailingDirectives: Option[Seq[Directive]]
      ) =>
        {
          val defaultValue = dv.collect {
            case PArrayValue(Nil, _) =>
              PArrayValue(Nil, ht match {
                case PArray(ptype) => ptype
                case _             => PAny
              })
            case nonArray => nonArray
          }
          PModelField(
            id,
            ht,
            defaultValue,
            ds.concat(trailingDirectives.getOrElse(Nil)).toList,
            Some(PositionRange(start, end))
          )
        }
    }
  }

  def enumDef: Rule1[PEnum] = rule {
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
          val variants = values.map {
            case PStringValue(s) => s
            case s: String       => s
          }.toList
          PEnum(id, variants, Some(PositionRange(start, end)))
        }
    }
  }

  def importDef: Rule1[PImport] = rule {
    "import" ~ stringVal ~ "as" ~ push(cursor) ~ identifier ~ push(cursor) ~> {
      (
          file: PStringValue,
          start: Int,
          id: String,
          end: Int
      ) =>
        PImport(id, file.value, Some(PositionRange(start, end)))
    }
  }

  def configDef: Rule1[PConfig] = rule {
    push(cursor) ~ "config" ~ push(cursor) ~ "{" ~ zeroOrMore(configEntry) ~ "}" ~>
      ((start: Int, end: Int, entries: Seq[ConfigEntry]) => {
        PConfig(entries.toList, Some(PositionRange(start, end)))
      })
  }

  def configEntry: Rule1[ConfigEntry] = rule {
    push(cursor) ~ identifier ~ push(cursor) ~ "=" ~
      literal ~ optional(",") ~ whitespace() ~> {
      (
          start: Int,
          key: String,
          end: Int,
          value: PValue
      ) =>
        ConfigEntry(key, value, Some(PositionRange(start, end)))
    }
  }

  def event: Rule1[PEvent] = rule {
    valueMap(
      Map(
        "CREATE" -> Create,
        "READ" -> Read,
        "UPDATE" -> Update,
        "DELETE" -> Delete,
        "LIST" -> ReadMany,
        "MUTATE" -> Mutate,
        "PUSH_TO" -> PushTo(),
        "SET_ON_CREATE" -> SetOnCreate,
        "REMOVE_FROM" -> RemoveFrom(),
        "RECOVER" -> Recover
      )
    )
  }

  def singletonEvent: Rule1[List[PEvent]] = rule {
    event ~> ((event: PEvent) => event :: Nil)
  }

  def allEvents: Rule1[List[PEvent]] = rule { "ALL" ~ push(All :: Nil) }

  def eventsList: Rule1[List[PEvent]] = rule {
    ("[" ~ oneOrMore(event).separatedBy(",") ~ "]") ~>
      ((events: Seq[PEvent]) => events.toList)
  }

  def ref: Rule1[Reference] = rule {
    push(cursor) ~ oneOrMore(identifier)
      .separatedBy(ch('.')) ~ push(cursor) ~> {
      (
          start: Int,
          path: Seq[String],
          end: Int
      ) =>
        Reference(path.toList, Some(PositionRange(start, end)))
    }
  }

  def accessRuleDef: Rule1[AccessRule] = rule {
    push(cursor) ~
      valueMap(Map("allow" -> Allow, "deny" -> Deny)) ~
      whitespace() ~ (singletonEvent | eventsList | allEvents) ~
      whitespace() ~ ref ~
      optional(whitespace() ~ ref) ~
      push(cursor) ~> {
      (
          start: Int,
          ruleKind: RuleKind,
          events: List[PEvent],
          resource: Reference,
          predicate: Option[Reference],
          end: Int
      ) =>
        AccessRule(
          ruleKind,
          (
            Reference(resource.path.head :: Nil),
            resource.path.lift(1).map(child => Reference(child :: Nil))
          ),
          events,
          predicate,
          Some(PositionRange(start, end))
        )
    }
  }

  def roleDef: Rule1[Role] = rule {
    "role" ~ push(cursor) ~ identifier ~ push(cursor) ~ "{" ~
      zeroOrMore(accessRuleDef) ~ "}" ~> {
      (start: Int, roleName: String, end: Int, rules: Seq[AccessRule]) =>
        Role(PReference(roleName), rules.toList)
    }
  }

}
