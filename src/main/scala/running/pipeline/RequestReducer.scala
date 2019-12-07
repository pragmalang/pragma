package running.pipeline
import domain.SyntaxTree
import scala.util.Try
import sangria._, ast._, execution._

case class RequestReducer(syntaxTree: SyntaxTree, request: Request)
    extends PiplineFunction[Request, Try[Request]] {

  override def apply(input: Request = request): Try[Request] =
    Try(
      request.copy(
        ctx =
          request.ctx.copy(graphQlQuery = reduceQuery(request.ctx.graphQlQuery))
      )
    )

  def reduceQuery(queryAst: Document): Document = ???
}

object RequestReducer {
  def spreadFragmentSpreads(
      selectable: SelectionContainer,
      queryAst: Document
  ): SelectionContainer =
    selectable match {
      case field: Field =>
        field.copy(
          selections = substituteFragmentSpreads(field.selections, queryAst)
        )
      case fragmentDefinition: FragmentDefinition =>
        fragmentDefinition.copy(
          selections =
            substituteFragmentSpreads(fragmentDefinition.selections, queryAst)
        )
      case inlineFragment: InlineFragment =>
        inlineFragment.copy(
          selections =
            substituteFragmentSpreads(inlineFragment.selections, queryAst)
        )
      case operationDefinition: OperationDefinition =>
        operationDefinition.copy(
          selections =
            substituteFragmentSpreads(operationDefinition.selections, queryAst)
        )
    }

  def substituteFragmentSpreads(
      selections: Vector[Selection],
      queryAst: Document
  ): Vector[Selection] =
    selections.foldLeft(Vector.empty[Selection]) { (acc, selection) =>
      selection match {
        case selection: Field =>
          acc :+ selection.copy(
            selections = substituteFragmentSpreads(selection.selections, queryAst)
          )
        case selection: InlineFragment =>
          acc ++ substituteFragmentSpreads(selection.selections, queryAst)
        case selection: FragmentSpread =>
          acc ++ substituteFragmentSpreads(
            queryAst.definitions
              .filter {
                case _: FragmentDefinition => true
                case _ => false
              }
              .map(_.asInstanceOf[FragmentDefinition])
              .find(_.name == selection.name)
              .get
              .selections,
            queryAst
          )
      }
    }

  def operationAndFragmentDefinitions(
      queryAst: Document
  ): Vector[SelectionContainer] =
    queryAst.definitions
      .filter {
        case _: OperationDefinition => true
        case _: FragmentDefinition  => true
        case _                      => false
      }
      .map(_.asInstanceOf[SelectionContainer])
}
