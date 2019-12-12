package domain
import scala.collection.immutable.ListMap
import domain.primitives._
import spray.json._
import scala.util.{Try, Success, Failure}
import java.time.ZonedDateTime

package object utils {
  trait Identifiable {
    val id: String
  }

  type NamedArgs = ListMap[String, HType]
  type PositionalArgs = List[HType]
  type Args = Either[PositionalArgs, NamedArgs]
  type Date = java.time.ZonedDateTime

  class InternalException(message: String)
      extends Exception(s"Internal Exception: ${message}")
  class TypeMismatchException(expected: List[HType], found: HType)
      extends InternalException(
        s"Type Mismatch. Expected ${if (expected.length == 1) displayHType(expected.head)
        else s"one of [${expected.map(displayHType(_)).mkString(", ")}]"}, but found ${displayHType(found)}"
      )

  type ErrorMessage = (String, Option[PositionRange])

  class UserError(val errors: List[ErrorMessage])
      extends Exception(errors.map(_._1).mkString("\n"))

  def displayHType(hType: HType, isVerbose: Boolean = true): String =
    hType match {
      case HAny     => "Any"
      case HString  => "String"
      case HInteger => "Integer"
      case HFloat   => "Float"
      case HBool    => "Boolean"
      case HDate    => "Date"
      case HFile(size, exts) =>
        if (isVerbose) s"File { size = $size, extensions = $exts }" else "File"
      case HArray(t)  => s"[${displayHType(t, isVerbose = false)}]"
      case HOption(t) => s"${displayHType(t, isVerbose = false)}?"
      case HModel(id, fields, directives, _) =>
        if (isVerbose)
          s"${directives.map(displayDirective).mkString("\n")}\nmodel $id {\n${fields.map(displayField).map("  " + _).mkString("\n")}\n}"
        else id
      case HInterface(id, fields, position) =>
        if (isVerbose)
          s"interface $id {\n${fields.map(displayField).mkString("\n")}\n}"
        else id
      case HEnum(id, values, _) =>
        if (isVerbose)
          s"enum $id {\n${values.map("  " + _).mkString("\n")}\n}"
        else id
      case HReference(id) => id
      case HFunction(namedArgs, returnType) => {
        val args = namedArgs
          .map(arg => displayHType(arg._2))
          .mkString(", ")
        s"($args) => ${displayHType(returnType)}"
      }
      case i: Identifiable => i.id
    }

  def displayField(field: HShapeField): String = field match {
    case HInterfaceField(id, htype, position) => s"$id: ${displayHType(htype)}"
    case HModelField(id, htype, defaultValue, directives, position) =>
      s"$id: ${displayHType(htype, isVerbose = false)} ${directives.map(displayDirective).mkString(" ")}"
  }

  def displayDirective(directive: Directive) = {
    val args =
      s"(${directive.args.value.map(arg => s"${arg._1}: ${displayHValue(arg._2)}").mkString(", ")})"
    directive.args.value.isEmpty match {
      case true  => s"@${directive.id}"
      case false => s"@${directive.id}${args}"
    }
  }

  def displayHValue(value: HValue): String = value match {
    case HIntegerValue(value) => value.toString
    case HFloatValue(value)   => value.toString
    case HFileValue(value, htype) => {
      val name = value.getName()
      name match {
        case "" => "File {}"
        case _  => s"File { name: $name }"
      }
    }
    case HModelValue(value, htype) =>
      s"{\n${value.map(v => s" ${v._1}: ${displayHValue(v._2)}").mkString(",\n")}\n}"
    case HOptionValue(value, valueType) => value.map(displayHValue).mkString
    case HStringValue(value)            => value
    case HDateValue(value)              => value.toString
    case HBoolValue(value)              => value.toString
    case f: HFunctionValue[_, _] =>
      s"(${f.htype.args.map(
        arg => s"${arg._1}: ${displayHType(arg._2, isVerbose = false)}"
      )}) => ${displayHType(f.htype.returnType, isVerbose = false)}"
    case HInterfaceValue(value, htype) =>
      s"{\n${value.map(v => s" ${v._1}: ${v._2}").mkString(",\n")}\n}"
    case HArrayValue(values, elementType) =>
      s"[${values.map(displayHValue).mkString(", ")}]"
  }

  def typeCheckJson(htype: HType, syntaxTree: SyntaxTree)(
      json: JsValue
  ): Try[JsValue] =
    Try {
      def fieldsRespectsShape(
          objectFields: Map[String, JsValue],
          shapeFields: List[HShapeField]
      ) =
        objectFields.forall(
          of =>
            shapeFields.count(
              sf =>
                sf.id == of._1 &&
                  typeCheckJson(sf.htype, syntaxTree)(of._2).isSuccess
            ) == 1
        ) && shapeFields
          .filterNot(_.isOptional)
          .forall(
            sf =>
              objectFields.count(
                of =>
                  sf.id == of._1 &&
                    typeCheckJson(sf.htype, syntaxTree)(of._2).isSuccess
              ) == 1
          )
      (htype, json) match {
        case (HDate, JsString(v)) if Try(ZonedDateTime.parse(v)).isSuccess =>
          json
        case (HDate, _) =>
          throw new Exception("Date must be a valid ISO date")
        case (HArray(htype), JsArray(elements))
            if elements
              .map(e => typeCheckJson(htype, syntaxTree)(e))
              .forall {
                case Failure(_) => false
                case Success(_) => true
              } =>
          json
        case (HInteger, JsNumber(v)) if v.isWhole => json
        case (HBool, JsBoolean(v))                => json
        case (HOption(htype), JsNull)             => json
        case (HOption(htype), _)                  => typeCheckJson(htype, syntaxTree)(json).get
        case (HString, JsString(v))               => json
        case (HFloat, JsNumber(value))            => json
        case (shape: HShape, JsObject(fields))
            if fieldsRespectsShape(fields, shape.fields) =>
          json
        case (HSelf(id), _: JsValue) =>
          typeCheckJson(syntaxTree.findTypeById(id).get, syntaxTree)(json).get
        case (HReference(id), _: JsValue) =>
          typeCheckJson(syntaxTree.findTypeById(id).get, syntaxTree)(json).get
        case (henum: HEnum, JsString(value)) if henum.values.contains(value) =>
          json
        case (htype: HType, json: JsValue) =>
          throw new Exception(
            s"The provided JSON value:\n${json.prettyPrint}\ndoesn't pass type validation against type ${displayHType(htype, isVerbose = false)}"
          )
      }
    }
}
