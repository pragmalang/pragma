package parsing

import org.parboiled2._
import domain._
import domain.utils.Identifiable
import spray.json.JsValue
import scala.util.{Try, Failure}
import domain.utils.UserError
import scala.language.implicitConversions

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
        new Exception(
          "Reference should not be executed before substitution"
        )
      )

    override val ptype: PFunction =
      PFunction(Map.empty, PAny)

    override def toString: String =
      path.head + path.tail.foldLeft("")(_ + "." + _)

    override val fields: List[PShapeField] = Nil

  }

  /** Represents a model/field index annotation
    *  (e.g. @1, @23)
    */
  case class IndexAnnotation(value: Int) extends Annotation

}
class PragmaParser(val input: ParserInput) extends Parser {
  import parsing.PragmaParser._

  def syntaxTree: Rule1[List[PConstruct]] = rule {
    wsWithEndline() ~
      zeroOrMore(
        importDef | modelDef | enumDef | configDef | accessRuleDef | roleDef
      ).separatedBy(wsWithEndline()) ~ wsWithEndline() ~>
      ((cs: Seq[PConstruct]) => cs.toList) ~ EOI
  }

  def comment = rule {
    '#' ~ zeroOrMore(noneOf("\n"))
  }

  // Parses a single whitespace character
  def wsChar(includeEndline: Boolean) = rule {
    anyOf(" \r\t" + (if (includeEndline) "\n" else ""))
  }

  def wsWithEndline(terminal: String = ""): Rule0 = rule {
    zeroOrMore(wsChar(true) | comment) ~
      str(terminal) ~
      zeroOrMore(wsChar(true) | comment)
  }

  def wsWithoutEndline(terminal: String = ""): Rule0 = rule {
    zeroOrMore(wsChar(false) | comment) ~
      str(terminal) ~
      zeroOrMore(wsChar(false) | comment)
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

  // Note: returns an array with unknown element type if the array is empty.
  def arrayVal: Rule1[PArrayValue] = rule {
    '[' ~ wsWithEndline() ~
      zeroOrMore(literal).separatedBy(optional(wsWithEndline(","))) ~
      wsWithEndline() ~ ']' ~>
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
    identifier ~ wsWithoutEndline(":") ~ (literal | ref) ~>
      ((key: String, value: PValue) => key -> value)
  }

  def namedArgs: Rule1[PInterfaceValue] = rule {
    zeroOrMore(namedArg).separatedBy(wsWithEndline(",")) ~> {
      (pairs: Seq[(String, PValue)]) =>
        PInterfaceValue(pairs.toMap, PInterface("", Nil, None))
    }
  }

  def indexAnnotation: Rule1[IndexAnnotation] = rule {
    '@' ~ integerVal ~>
      ((i: PIntValue) => IndexAnnotation(i.value.toInt))
  }

  def directive = rule {
    '@' ~ identifier ~ optional(
      '(' ~ wsWithEndline() ~ namedArgs ~ wsWithEndline() ~ ')'
    )
  }

  def directive(dirKind: DirectiveKind): Rule1[Directive] = rule {
    push(cursor) ~ directive ~ push(cursor) ~> {
      (start: Int, did: String, args: Option[PInterfaceValue], end: Int) =>
        Directive(did, args match {
          case Some(args) => args
          case None       => PInterfaceValue(Map.empty, PInterface("", Nil, None))
        }, dirKind, Some(PositionRange(start, end)))
    }
  }

  def annotation(directiveKind: DirectiveKind): Rule1[Annotation] = rule {
    indexAnnotation | directive(directiveKind)
  }

  def modelDef: Rule1[PModel] = rule {
    zeroOrMore(annotation(ModelDirective))
      .separatedBy(wsWithEndline()) ~ wsWithEndline() ~
      "model" ~ wsWithoutEndline() ~
      push(cursor) ~ identifier ~ push(cursor) ~
      wsWithEndline("{") ~
      zeroOrMore(fieldDef).separatedBy(wsWithEndline()) ~
      wsWithEndline() ~ '}' ~> {
      (
          anns: Seq[Annotation],
          start: Int,
          id: String,
          end: Int,
          fields: Seq[PModelField]
      ) =>
        {
          val modelIndexes = anns.collect { case IndexAnnotation(i) => i }
          if (modelIndexes.length == 0)
            throw UserError(
              s"Model $id must have an index (e.g. @1)",
              Some(PositionRange(start, end))
            )
          if (modelIndexes.length > 1)
            throw UserError(
              "A model cannot have more than one index",
              Some(PositionRange(start, end))
            )
          PModel(
            id,
            fields.toList,
            anns.collect { case d: Directive => d }.toList,
            modelIndexes.head,
            Some(PositionRange(start, end))
          )
        }
    }
  }

  def fieldDef: Rule1[PModelField] = rule {
    zeroOrMore(annotation(FieldDirective))
      .separatedBy(wsWithEndline()) ~
      wsWithEndline() ~ push(cursor) ~ identifier ~ push(cursor) ~
      wsWithoutEndline() ~ ":" ~ wsWithoutEndline() ~ ptype ~
      optional(wsWithoutEndline() ~ "=" ~ wsWithoutEndline() ~ literal) ~
      optional(
        wsWithoutEndline() ~
          zeroOrMore(annotation(FieldDirective))
            .separatedBy(wsWithoutEndline())
      ) ~ optional(",") ~> {
      (
          anns: Seq[Annotation],
          start: Int,
          id: String,
          end: Int,
          ht: PType,
          dv: Option[PValue],
          trailingAnnotations: Option[Seq[Annotation]]
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
          val allAnnotations = anns ++ trailingAnnotations.getOrElse(Nil)
          val fieldIndex =
            allAnnotations.collect { case i: IndexAnnotation => i }
          if (fieldIndex.length == 0)
            throw UserError(
              s"Model field `$id` must have an index (e.g. @1)",
              Some(PositionRange(start, end))
            )
          else if (fieldIndex.length > 1)
            throw UserError(
              s"Model field `$id` can only have one field index, ${fieldIndex.length} found",
              Some(PositionRange(start, end))
            )
          PModelField(
            id,
            ht,
            defaultValue,
            fieldIndex.head.value,
            allAnnotations.toList.collect { case d: Directive => d },
            Some(PositionRange(start, end))
          )
        }
    }
  }

  def enumDef: Rule1[PEnum] = rule {
    "enum" ~ wsWithoutEndline() ~
      push(cursor) ~ identifier ~ push(cursor) ~
      wsWithEndline() ~ '{' ~ wsWithEndline() ~
      zeroOrMore(identifier | stringVal)
        .separatedBy(wsWithEndline(",") | wsWithEndline()) ~
      wsWithEndline() ~ '}' ~> {
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
    "import" ~ wsWithoutEndline() ~ stringVal ~
      wsWithoutEndline("as") ~
      push(cursor) ~ identifier ~ push(cursor) ~> {
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
    push(cursor) ~ "config" ~ push(cursor) ~
      wsWithoutEndline() ~ '{' ~ wsWithEndline() ~
      zeroOrMore(configEntry)
        .separatedBy(wsWithEndline(",") | wsWithEndline()) ~
      wsWithEndline() ~ '}' ~>
      ((start: Int, end: Int, entries: Seq[ConfigEntry]) => {
        PConfig(entries.toList, Some(PositionRange(start, end)))
      })
  }

  def configEntry: Rule1[ConfigEntry] = rule {
    push(cursor) ~ identifier ~ push(cursor) ~
      wsWithoutEndline("=") ~ literal ~> {
      (
          start: Int,
          key: String,
          end: Int,
          value: PValue
      ) =>
        ConfigEntry(key, value, Some(PositionRange(start, end)))
    }
  }

  def permission: Rule1[PPermission] = rule {
    valueMap(
      Map(
        "ALL" -> All,
        "CREATE" -> Create,
        "READ" -> Read,
        "UPDATE" -> Update,
        "DELETE" -> Delete,
        "MUTATE" -> Mutate,
        "PUSH_TO" -> PushTo,
        "SET_ON_CREATE" -> SetOnCreate,
        "REMOVE_FROM" -> RemoveFrom,
        "LOGIN" -> Login
      )
    )
  }

  def singletonPermission: Rule1[List[PPermission]] = rule {
    permission ~> ((event: PPermission) => event :: Nil)
  }

  def permissionsList: Rule1[List[PPermission]] = rule {
    '[' ~ wsWithEndline() ~
      oneOrMore(permission).separatedBy(wsWithEndline(",") | wsWithEndline()) ~
      wsWithEndline() ~ ']' ~>
      ((events: Seq[PPermission]) => events.toList)
  }

  def ref: Rule1[Reference] = rule {
    push(cursor) ~ oneOrMore(identifier)
      .separatedBy('.') ~ push(cursor) ~> {
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
      wsWithEndline() ~ (singletonPermission | permissionsList) ~
      wsWithEndline() ~ ref ~
      optional(wsWithoutEndline("if") ~ ref) ~
      push(cursor) ~> {
      (
          start: Int,
          ruleKind: RuleKind,
          permissions: List[PPermission],
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
          permissions.toSet,
          predicate,
          Some(PositionRange(start, end))
        )
    }
  }

  def roleDef: Rule1[Role] = rule {
    "role" ~ wsWithoutEndline() ~
      push(cursor) ~ identifier ~ push(cursor) ~
      wsWithEndline() ~ '{' ~ wsWithEndline() ~
      zeroOrMore(accessRuleDef).separatedBy(wsWithEndline()) ~
      wsWithEndline() ~ '}' ~> {
      (_: Int, roleName: String, _: Int, rules: Seq[AccessRule]) =>
        Role(PReference(roleName), rules.toList)
    }
  }

}
