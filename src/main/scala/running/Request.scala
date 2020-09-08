package running

import spray.json._
import domain._
import sangria.ast._

case class Request(
    hookData: Option[JsValue],
    body: Option[JsObject],
    user: Option[JwtPayload],
    query: Document,
    queryVariables: JsObject,
    cookies: Map[String, String],
    url: String,
    hostname: String
)

object Request {
  lazy val pType: PInterface = PInterface(
    "Request",
    List(
      PInterfaceField("hookData", PAny, None),
      PInterfaceField("body", PAny, None),
      PInterfaceField("user", PAny, None),
      PInterfaceField("query", PAny, None),
      PInterfaceField("queryVariable", PAny, None),
      PInterfaceField("cookies", PAny, None),
      PInterfaceField("url", PAny, None),
      PInterfaceField("hostname", PAny, None)
    ),
    None
  )
}
