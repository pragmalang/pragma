package running
import domain.primitives.{ExternalFunction}
import domain.HType
import spray.json.{JsValue}
import scala.util.Try
import domain.primitives.`package`.PrimitiveType
import spray.json.JsNumber
import scala.util.Success
import domain.primitives.HDate
import domain.primitives.HArray
import domain.primitives.HInteger
import domain.primitives.HBool
import domain.primitives.HOption
import domain.primitives.HString
import domain.primitives.HFloat
import domain.primitives.HFile
import domain.primitives.HFunction
import spray.json.JsString
import scala.util.Failure
import domain.utils.`package`.TypeMismatchException
import spray.json.JsArray
import spray.json.JsBoolean
import spray.json.JsNull
import domain.HModel
import spray.json.JsObject
import domain.HShape

sealed trait ExternalFunctionExecutor {
  val function: ExternalFunction

  def execute(): JsValue

  def typeCheck(json: JsValue): Try[JsValue] =
    typeCheckJson(function.htype.returnType)(json)

  def typeCheckJson(htype: HType): JsValue => Try[JsValue] =
    (json: JsValue) =>
      htype match {
        case HDate =>
          json match {
            case JsString(value)
                if Try(java.time.ZonedDateTime.parse(value)).isSuccess =>
              Success(json)
            case _ => Failure(new Exception())
          }
        case HArray(htype) =>
          json match {
            case JsArray(elements)
                if elements
                  .map(e => typeCheckJson(htype)(e))
                  .contains(Failure(new Exception())) =>
              Success(json)
            case _ => Failure(new Exception())
          }
        case HInteger =>
          json match {
            case JsNumber(value) if value.isWhole => Success(json)
            case _                                => Failure(new Exception())
          }
        case HBool =>
          json match {
            case JsBoolean(value) => Success(json)
            case _                => Failure(new Exception())
          }
        case HOption(htype) =>
          json match {
            case JsNull => Success(json)
            case _      => typeCheckJson(htype)(json)
          }
        case HString =>
          json match {
            case JsString(value) => Success(json)
            case _               => Failure(new Exception())
          }
        case HFloat =>
          json match {
            case JsNumber(value) => Success(json)
            case _               => Failure(new Exception())
          }
        case shape: HShape =>
          json match {
            case JsObject(fields) => ???
            case _ => Failure(new Exception())
          }
      }
}

case class DockerFunctionExecutor(function: ExternalFunction)
    extends ExternalFunctionExecutor {
  def execute(): JsValue = ???
}

/*
f x = x + 1
g x = x * x
h = g . f
*/