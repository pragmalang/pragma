package running

import pragma.domain.AccessRule
import running.operations.OperationParser
import running.storage.QueryEngine
import running.storage.Storage
import cats.effect._

object TestUtils {

  def printRule(rule: AccessRule) = println {
    s"${rule.ruleKind} ${rule.permissions} ${rule.resourcePath._1.id}.${rule.resourcePath._2
      .map(_.id)} if ${rule.predicate.map(_ => "<predicate>")}"
  }

  /** Helper to run GQL queryies agains the `queryEngine` */
  def runGql[S <: Storage[S, IO]](
      gqlQuery: sangria.ast.Document
  )(implicit opParser: OperationParser, queryEngine: QueryEngine[S, IO]) = {
    val req = Request.bareReqFrom(gqlQuery)
    val reqOps = opParser.parse(req)
    val results = reqOps.map(queryEngine.run(_).unsafeRunSync) match {
      case Left(err) => throw err
      case Right(values) =>
        values.map {
          case (alias, vec) => alias -> vec.flatMap(_._2.map(_._2))
        }
    }
    results.toMap
  }

}
