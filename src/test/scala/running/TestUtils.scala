package running

import spray.json.JsObject
import sangria.ast.Document

object TestUtils {

  /** Helper to construct simple `Request`s */
  def bareReqFrom(gqlQuery: Document) =
    Request(
      hookData = None,
      body = None,
      user = None,
      query = gqlQuery,
      queryVariables = Left(JsObject.empty),
      cookies = Map.empty,
      url = "",
      hostname = ""
    )

}
