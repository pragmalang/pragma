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
import domain.HShapeField
import domain.HSelf
import domain.HReference
import domain.HEnum
import domain.SyntaxTree

import java.time.ZonedDateTime

sealed trait ExternalFunctionExecutor {
  def execute(): JsValue

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
        ) && shapeFields.forall(
          sf =>
            objectFields.count(
              of =>
                !sf.isOptional && sf.id == of._1 &&
                  typeCheckJson(sf.htype, syntaxTree)(of._2).isSuccess
            ) == 1
        )
      (htype, json) match {
        case (HDate, JsString(v)) if Try(ZonedDateTime.parse(v)).isSuccess => json
        case (HDate, _) =>
          throw new Exception("Date must be a valid ISO date")
        case (HArray(htype), JsArray(elements))
            if elements
              .map(e => typeCheckJson(htype, syntaxTree)(e))
              .exists {
                case Failure(_) => true
                case Success(_) => false
              } => json
        case (HInteger, JsNumber(v)) if v.isWhole => json
        case (HBool, JsBoolean(v))                => json
        case (HOption(htype), JsNull)             => json
        case (HOption(htype), _)                  => typeCheckJson(htype, syntaxTree)(json).get
        case (HString, JsString(v))               => json
        case (HFloat, JsNumber(value))            => json
        case (shape: HShape, JsObject(fields))
            if fieldsRespectsShape(fields, shape.fields) => json
        case (HSelf(id), jsValue: JsValue)      => typeCheckJson(syntaxTree.findTypeById(id).get, syntaxTree)(json).get
        case (HReference(id), jsValue: JsValue) => typeCheckJson(syntaxTree.findTypeById(id).get, syntaxTree)(json).get
        case (henum: HEnum, JsString(value)) if henum.values.contains(value) => json
      }
    }
}

case class DockerFunctionExecutor(function: ExternalFunction)
    extends ExternalFunctionExecutor {
  def execute(): JsValue = ???
}