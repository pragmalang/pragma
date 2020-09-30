package running

import sangria._, ast._
import spray.json._
import utils._

object RequestReducer {

  def apply(input: Request): Request =
    input.copy(
      query = RequestReducer
        .reduceQuery(input.query, input.queryVariables)
    )

  def reduceQuery(queryAst: Document, variables: JsObject): Document =
    Document(queryAst.definitions.collect {
      case operationDefinition: OperationDefinition =>
        substitute(operationDefinition, queryAst, variables)
          .asInstanceOf[OperationDefinition]
      case fragmentDefinition: FragmentDefinition => fragmentDefinition
    })

  /**
    * Assumes that `query` has been passed to `spreadFragmentSpreads`
    */
  def substituteVariablesAndFragments(
      selections: Vector[Selection],
      queryAst: Document,
      variables: JsObject
  ): Vector[Selection] =
    selections.foldLeft(Vector.empty[Selection]) { (acc, selection) =>
      selection match {
        case selection: Field =>
          acc :+ selection.copy(
            selections = substituteVariablesAndFragments(
              selection.selections,
              queryAst,
              variables
            ),
            arguments = selection.arguments.map {
              case arg @ Argument(_, VariableValue(name, _, _), _, _) =>
                arg.copy(value = jsonToSangria(variables.fields(name)))
              case arg => arg
            }
          )
        case selection: InlineFragment =>
          acc ++ substituteVariablesAndFragments(
            selection.selections,
            queryAst,
            variables
          )
        case selection: FragmentSpread =>
          acc ++ substituteVariablesAndFragments(
            queryAst.definitions
              .collect {
                case s: FragmentDefinition => s
              }
              .find(_.name == selection.name)
              .get
              .selections,
            queryAst,
            variables
          )
      }
    }

  def substitute(
      selectable: SelectionContainer,
      queryAst: Document,
      variables: JsObject
  ): SelectionContainer =
    selectable match {
      case field: Field =>
        field.copy(
          selections = substituteVariablesAndFragments(
            field.selections,
            queryAst,
            variables
          )
        )
      case fragmentDefinition: FragmentDefinition =>
        fragmentDefinition.copy(
          selections = substituteVariablesAndFragments(
            fragmentDefinition.selections,
            queryAst,
            variables
          )
        )
      case inlineFragment: InlineFragment =>
        inlineFragment.copy(
          selections = substituteVariablesAndFragments(
            inlineFragment.selections,
            queryAst,
            variables
          )
        )
      case operationDefinition: OperationDefinition =>
        operationDefinition.copy(
          selections = substituteVariablesAndFragments(
            operationDefinition.selections,
            queryAst,
            variables
          )
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
              .collect {
                case s: FragmentDefinition => s
              }
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
      .collect {
        case op: OperationDefinition => op
        case f: FragmentDefinition   => f
      }
}
