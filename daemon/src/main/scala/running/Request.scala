package running

import spray.json._
import pragma.domain._, pragma.jwtUtils._
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

  /** Helper to construct simple `Request`s */
  def bareReqFrom(gqlQuery: Document) =
    Request(
      hookData = None,
      body = None,
      user = None,
      query = gqlQuery,
      queryVariables = JsObject.empty,
      cookies = Map.empty,
      url = "",
      hostname = ""
    )

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
