package setup
import sangria.parser.QueryParser
import scala.language.implicitConversions
import scala.language.postfixOps
import sys.process._
import scala.util.{Success, Failure}
import spray.json._
import domain.Implicits._

package object Implicits {
  implicit def parseQuery(query: String) = QueryParser.parse(query)

  implicit class JsValueMethods(value: JsValue) {
    def renderYaml(level: Int = 0): String = value match {
      case value: JsBoolean => value.toString
      case JsNull           => ""
      case JsObject(fields) =>
        fields.foldLeft("")(
          (acc, el) =>
            el._2 match {
              case v
                  if fields.toList
                    .map(_._2)
                    .indexOf(v) == fields.toList.length - 1 =>
                v match {
                  case JsArray(elements) =>
                    acc + (s"${el._1}:\n${el._2.renderYaml(level + 1)}")
                      .indent(level)
                  case JsObject(fields) =>
                    acc + (s"${el._1}:\n${el._2.renderYaml(level + 1)}")
                      .indent(level)
                  case _ =>
                    acc + (s"${el._1}: ${el._2.renderYaml(level + 1)}")
                      .indent(level)
                }
              case JsArray(elements) =>
                acc + (s"${el._1}:\n${el._2.renderYaml(level + 1)}")
                  .indent(level) + "\n"
              case JsObject(fields) =>
                acc + (s"${el._1}:\n${el._2.renderYaml(level + 1)}")
                  .indent(level) + "\n"
              case _ =>
                acc + (s"${el._1}: ${el._2.renderYaml(level + 1)}")
                  .indent(level) + "\n"
            }
        )
      case JsNumber(value) => value.toString
      case JsArray(elements) =>
        elements.foldLeft("")(
          (acc, el) =>
            acc + (s"- ${el.renderYaml(level + 1)}").indent(level) + "\n"
        )
      case JsString(value) => s"$value"
    }
  }
}
