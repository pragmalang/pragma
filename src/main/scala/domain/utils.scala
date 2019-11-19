package domain
import scala.collection.immutable.ListMap
import primitives._
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

  implicit class StringMethods(s: String) {
    def small = s.updated(0, s.head.toString.toLowerCase.head)
  }

  def displayHType(hType: HType): String = hType match {
    case HString           => "String"
    case HInteger          => "Integer"
    case HFloat            => "Float"
    case HBool             => "Boolean"
    case HDate             => "Date"
    case HFile(size, exts) => s"File { size = $size, extensions = $exts }"
    case HArray(t)         => s"[${displayHType(t)}]"
    case HOption(t)        => s"${displayHType(t)}?"
    case HReference(id)    => id
    case i: Identifiable   => i.id
    case HFunction(namedArgs, returnType) => {
      val args = namedArgs
        .map(arg => displayHType(arg._2))
        .mkString(", ")
      s"($args) => ${displayHType(returnType)}"
    }
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
              .exists {
                case Failure(_) => true
                case Success(_) => false
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
      }
    }
}
