package running

import pragma.domain.AccessRule

object TestUtils {

  def printRule(rule: AccessRule) = println {
    s"${rule.ruleKind} ${rule.permissions} ${rule.resourcePath._1.id}.${rule.resourcePath._2
      .map(_.id)} if ${rule.predicate.map(_ => "<predicate>")}"
  }

}
