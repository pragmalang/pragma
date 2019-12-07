package running

package object utils {
  import sangria.ast._
  import scala.util.Try
  def extractModelNamesFromQuery(queryAst: Document): Try[List[String]] = Try {
    val operationDefinitions = queryAst.definitions
      .filter(_.isInstanceOf[OperationDefinition])
      .map(_.asInstanceOf[OperationDefinition])
      .map { op =>
        op.copy(selections = op.selections.filter {
          case _: Field          => true
          case _: FragmentSpread => true
          case _                 => false
        })
      }
    ???
  }
}