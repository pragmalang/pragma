package domain

import domain._
import spray.json._
import scala.util.{Try, Success, Failure}
import java.time.ZonedDateTime

package utils {

  trait Identifiable {
    val id: String
  }

  case class InternalException(message: String)
      extends Exception(s"Internal Exception: ${message}")

  class InvalidRequestHasPassed(message: String)
      extends InternalException(message)

  case class AuthorizationError(
      message: String,
      cause: Option[String] = None,
      suggestion: Option[String] = None,
      position: Option[PositionRange] = None
  ) extends Exception(
        s"Authorization Error: $message${cause.map(". " + _).getOrElse("")}${suggestion.map(". " + _).getOrElse("")}"
      )

  class TypeMismatchException(expected: Iterable[PType], found: PType)
      extends InternalException(
        s"Type Mismatch. Expected ${if (expected.size == 1) displayPType(expected.head)
        else s"one of [${expected.map(displayPType(_)).mkString(", ")}]"}, but found ${displayPType(found)}"
      )

  case class UserError(val errors: Iterable[ErrorMessage])
      extends Exception(errors.map(_._1).mkString("; "))

  object UserError {
    def apply(
        errorMessage: String,
        position: Option[PositionRange] = None
    ): UserError =
      UserError(List(errorMessage -> position))

    def apply(
        errorMessages: String*
    ): UserError =
      UserError(errorMessages.map(_ -> None).toList)

    def fromAuthErrors(authErrors: Iterable[AuthorizationError]): UserError =
      UserError(authErrors.map(_.message -> None))
  }

}
package object utils {

  type ID = String
  type ModelId = String
  type FieldId = String

  type FieldPath = (ModelId, FieldId)

  type NamedArgs = Map[String, PType]
  type Date = java.time.ZonedDateTime

  type InternalExceptionOr[A] = Either[InternalException, A]

  type ErrorMessage = (String, Option[PositionRange])

  def userErrorFrom[T](value: Try[T], exception: UserError): Try[T] =
    value match {
      case Failure(_)     => Failure(exception)
      case Success(value) => Success(value)
    }

  /** Takes a list of `Try`s that may have `UserError`s inside
    * them, and combines them into a single `Try` that might contain a `UserError`
    */
  def combineUserErrorTries[T](ts: Seq[Try[T]]): Try[Seq[T]] =
    ts.foldLeft(Try(List.empty[T])) {
      case (Success(values), Success(value))     => Success(values :+ value)
      case (Success(_), Failure(err: UserError)) => Failure(err)
      case (Failure(err: UserError), Success(_)) => Failure(err)
      case (Failure(err: UserError), Failure(err2: UserError)) =>
        Failure(UserError(err.errors ++ err2.errors))
      case (Failure(otherError), _) => Failure(otherError)
      case (_, Failure(otherError)) => Failure(otherError)
    }

  def displayPType(ptype: PType, isVerbose: Boolean = false): String =
    ptype match {
      case PAny    => "Any"
      case PString => "String"
      case PInt    => "Int"
      case PFloat  => "Float"
      case PBool   => "Boolean"
      case PDate   => "Date"
      case PFile(size, exts) =>
        if (isVerbose) s"File { size = $size, extensions = $exts }" else "File"
      case PArray(t)  => s"[${displayPType(t, isVerbose = false)}]"
      case POption(t) => s"${displayPType(t, isVerbose = false)}?"
      case model: PModel =>
        if (isVerbose) {
          val renderedModel =
            s"model $model.id {\n${model.fields.map(displayField).map("  " + _).mkString("\n")}\n}"
          if (model.directives.isEmpty) {
            renderedModel
          } else {
            s"${model.directives.map(displayDirective).mkString("\n")}\n$renderedModel"
          }
        } else model.id
      case PInterface(id, fields, _) =>
        if (isVerbose)
          s"interface $id {\n${fields.map(displayField).mkString("\n")}\n}"
        else id
      case PEnum(id, values, _) =>
        if (isVerbose)
          s"enum $id {\n${values.map("  " + _).mkString("\n")}\n}"
        else id
      case PReference(id) => id
      case PFunction(namedArgs, returnType) => {
        val args = namedArgs
          .map(arg => displayPType(arg._2, isVerbose))
          .mkString(", ")
        s"($args) => ${displayPType(returnType, isVerbose)}"
      }
    }

  def displayField(field: PShapeField): String = field match {
    case PInterfaceField(id, ptype, _) => s"$id: ${displayPType(ptype)}"
    case PModelField(id, ptype, _, _, directives, _) =>
      s"$id: ${displayPType(ptype, isVerbose = false)} ${directives.map(displayDirective).mkString(" ")}"
  }

  def displayDirective(directive: Directive) = {
    val args =
      s"(${directive.args.value.map(arg => s"${arg._1}: ${displayPValue(arg._2)}").mkString(", ")})"
    directive.args.value.isEmpty match {
      case true  => s"@${directive.id}"
      case false => s"@${directive.id}${args}"
    }
  }

  def displayPValue(value: PValue): String = value match {
    case PIntValue(value)   => value.toString
    case PFloatValue(value) => value.toString
    case PFileValue(value, _) => {
      val name = value.getName()
      name match {
        case "" => "File {}"
        case _  => s"File { name: $name }"
      }
    }
    case PModelValue(value, _) =>
      s"{\n${value.map(v => s" ${v._1}: ${displayPValue(v._2)}").mkString(",\n")}\n}"
    case POptionValue(value, _) =>
      value.map(displayPValue) match {
        case Some(value) => value
        case None        => ""
      }
    case PStringValue(value) => s""""$value""""
    case PDateValue(value)   => value.toString
    case PBoolValue(value)   => value.toString
    case f: PFunctionValue[_, _] =>
      s"(${f.ptype.args.map(
        arg => s"${arg._1}: ${displayPType(arg._2, isVerbose = false)}"
      )}) => ${displayPType(f.ptype.returnType, isVerbose = false)}"
    case PInterfaceValue(value, _) =>
      s"{\n${value.map(v => s" ${v._1}: ${v._2}").mkString(",\n")}\n}"
    case PArrayValue(values, _) =>
      s"[${values.map(displayPValue).mkString(", ")}]"
  }

  def typeCheckJson(ptype: PType, syntaxTree: SyntaxTree)(
      json: JsValue
  ): Try[JsValue] =
    Try {
      def fieldsRespectsShape(
          objectFields: Map[String, JsValue],
          shapeFields: Iterable[PShapeField]
      ) =
        objectFields.forall(
          of =>
            shapeFields.count(
              sf =>
                sf.id == of._1 &&
                  typeCheckJson(sf.ptype, syntaxTree)(of._2).isSuccess
            ) == 1
        ) && shapeFields
          .filterNot(_.isOptional)
          .forall(
            sf =>
              objectFields.count(
                of =>
                  sf.id == of._1 &&
                    typeCheckJson(sf.ptype, syntaxTree)(of._2).isSuccess
              ) == 1
          )
      (ptype, json) match {
        case (PDate, JsString(v)) if Try(ZonedDateTime.parse(v)).isSuccess =>
          json
        case (PDate, _) =>
          throw new Exception("Date must be a valid ISO date")
        case (PArray(ptype), JsArray(elements))
            if elements
              .map(e => typeCheckJson(ptype, syntaxTree)(e))
              .forall {
                case Failure(_) => false
                case Success(_) => true
              } =>
          json
        case (PInt, JsNumber(v)) if v.isWhole => json
        case (PBool, JsBoolean(_))            => json
        case (POption(_), JsNull)             => json
        case (POption(ptype), _)              => typeCheckJson(ptype, syntaxTree)(json).get
        case (PString, JsString(_))           => json
        case (PFloat, JsNumber(_))            => json
        case (shape: PShape, JsObject(fields))
            if fieldsRespectsShape(fields, shape.fields) =>
          json
        case (PReference(id), _: JsValue) =>
          typeCheckJson(syntaxTree.findTypeById(id).get, syntaxTree)(json).get
        case (penum: PEnum, JsString(value)) if penum.values.contains(value) =>
          json
        case (PAny, _) => json
        case (ptype: PType, json: JsValue) =>
          throw new Exception(
            s"The provided JSON value:\n${json.prettyPrint}\ndoesn't pass type validation against type ${displayPType(ptype, isVerbose = false)}"
          )
      }
    }

  def displayInnerType(ptype: PType, isVerbose: Boolean = false): String =
    ptype match {
      case PArray(t)  => displayPType(t, isVerbose)
      case POption(t) => displayPType(t, isVerbose)
      case _          => displayPType(ptype, isVerbose)
    }
}
