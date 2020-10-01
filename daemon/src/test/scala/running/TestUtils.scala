package running

import spray.json.JsObject
import sangria.ast.Document
import pragma.domain.AccessRule

object TestUtils {

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

  def printRule(rule: AccessRule) = println {
    s"${rule.ruleKind} ${rule.permissions} ${rule.resourcePath._1.id}.${rule.resourcePath._2
      .map(_.id)} if ${rule.predicate.map(_ => "<predicate>")}"
  }

}
