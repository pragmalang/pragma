package running
import sangria.ast.{Document, Definition}
import spray.json._
import spray.json.DefaultJsonProtocol._

package object Implicits {
  implicit object GraphQlDocumentJsonFormater extends JsonFormat[Document] {
    def read(json: JsValue): Document = ???
    def write(obj: Document): JsValue = ???
  }

  implicit object GraphQlDefinitionJsonFormater extends JsonFormat[Definition] {
    def read(json: JsValue): Definition = ???
    def write(obj: Definition): JsValue = ???
  }

  implicit object JwtPaylodJsonFormater extends JsonFormat[JwtPaylod] {
    def read(json: JsValue): JwtPaylod = json match {
      case JsObject(fields) =>
        JwtPaylod(
          fields("userId").convertTo[String],
          fields("role").convertTo[String]
        )
      case _ => throw new Exception("Invalid JWT payload")
    }
    def write(obj: JwtPaylod): JsValue =
      JsObject("userId" -> JsString(obj.userId), "role" -> JsString(obj.role))
  }

  implicit object RequestJsonFormater extends JsonFormat[Request] {
    def read(json: JsValue): Request = ???
    def write(obj: Request): JsValue = ???
  }

  implicit object ContextJsonFormater extends JsonFormat[Context] {
    def read(json: JsValue): Context = ???
    def write(obj: Context): JsValue = ???
  }
}
