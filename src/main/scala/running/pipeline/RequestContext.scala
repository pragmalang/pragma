package running.pipeline

import spray.json._
import sangria.ast.Document
import running._
import domain._

case class RequestContext(
    user: Option[JwtPaylod],
    query: Document,
    queryVariables: Either[JsObject, List[JsObject]],
    cookies: Map[String, String],
    url: String,
    hostname: String
) {
  lazy val operation: HEvent = ???
  lazy val model: HModel = ???
  lazy val hooks = ???
}

object RequestContext {
  lazy val hType: HInterface =
    HInterface(
      "RequestContext",
      List(
        HInterfaceField("user", HAny, None),
        HInterfaceField("query", HAny, None),
        HInterfaceField("queryVariable", HAny, None),
        HInterfaceField("cookies", HAny, None),
        HInterfaceField("url", HAny, None),
        HInterfaceField("hostname", HAny, None)
      ),
      None
    )
}
