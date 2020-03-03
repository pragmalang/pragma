package running.pipeline.functions

import running.pipeline._
import domain.SyntaxTree
import scala.util.Try
import sangria._, ast._
import spray.json._

case class RequestReducer(syntaxTree: SyntaxTree)
    extends PiplineFunction[Request, Try[Request]] {

  override def apply(input: Request): Try[Request] =
    Try {
      input.copy(
        query = RequestReducer
          .reduceQuery(
            syntaxTree,
            input.query,
            Some(input.queryVariables)
          )
      )
    }
}

object RequestReducer {

  def reduceQuery(
      syntaxTree: SyntaxTree,
      queryAst: Document,
      variables: Option[Either[JsObject, List[JsObject]]]
  ): Document =
    Document(queryAst.definitions.collect {
      case operationDefinition: OperationDefinition =>
        RequestReducer
          .spreadFragmentSpreads(operationDefinition, queryAst)
          .asInstanceOf[OperationDefinition]
      case fragmentDefinition: FragmentDefinition => fragmentDefinition
    })

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
            selections =
              substituteFragmentSpreads(selection.selections, queryAst)
          )
        case selection: InlineFragment =>
          acc ++ substituteFragmentSpreads(selection.selections, queryAst)
        case selection: FragmentSpread =>
          acc ++ substituteFragmentSpreads(
            queryAst.definitions
              .filter {
                case _: FragmentDefinition => true
                case _                     => false
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
