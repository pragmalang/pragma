package setup

import sangria.parser.QueryParser
import scala.language.implicitConversions
import spray.json._

object SetupImplicits {
  implicit def parseQuery(query: String) = QueryParser.parse(query)

  implicit class JsValueMethods(value: JsValue) {
    def renderYaml(
        level: Int = 0,
        isInnerArrayOrObject: Boolean = false
    ): String =
      value match {
        case value: JsBoolean => value.toString
        case JsNull           => ""
        case arr: JsArray if isInnerArrayOrObject =>
          arr
            .renderYaml(level, false)
            .slice(8, arr.renderYaml(level, false).length)
        case obj: JsObject if isInnerArrayOrObject =>
          obj
            .renderYaml(level, false)
            .slice(8, obj.renderYaml(level, false).length)
        case JsObject(fields) =>
          fields.foldLeft("")(
            (acc, el) =>
              el._2 match {
                case JsObject(_) =>
                  acc + (s"${el._1}:\n${el._2.renderYaml(level + 1, false)}")
                    .indent(level)
                case arr: JsArray
                    if fields.toList
                      .map(_._2)
                      .indexOf(arr) == fields.toList.length - 1 =>
                  acc + (s"${el._1}:\n${el._2.renderYaml(level + 1, false)}")
                    .indent(level)
                case JsArray(_) =>
                  acc + (s"${el._1}:\n${el._2.renderYaml(level + 1, false)}")
                    .indent(level) + "\n"
                case _ =>
                  acc + (s"${el._1}: ${el._2.renderYaml(level + 1, false)}")
                    .indent(level) + "\n"
              }
          )
        case JsNumber(value) => value.toString
        case JsArray(elements) =>
          elements.foldLeft("")(
            (acc, el) =>
              el match {
                case JsObject(_) =>
                  acc + (s"- ${el.renderYaml(level + 1, true)}")
                    .indent(level) + "\n"
                case JsArray(_) =>
                  acc + (s"- ${el.renderYaml(level + 1, true)}")
                    .indent(level)
                case _ =>
                  acc + (s"- ${el.renderYaml(level + 1, false)}")
                    .indent(level) + "\n"
              }
          )
        case JsString(value) => s"$value"
      }
  }
}
