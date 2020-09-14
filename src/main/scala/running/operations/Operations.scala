package running.operations

import domain._
import sangria.ast._

object Operations {
  type OperationGroupName = String
  type ModelSelectionName = String
  type OperationsMap =
    Map[Option[OperationGroupName], Map[ModelSelectionName, Vector[Operation]]]

  case class AliasedField(
      field: PShapeField,
      alias: Option[String] = None,
      directives: Vector[sangria.ast.Directive]
  )
  type FieldSelection = Field
  type GqlOperationType = OperationType

  /** Utility function to get an inner operation
    * to read the primary field of a model.
    */
  def primaryFieldInnerOp(model: PModel): InnerReadOperation =
    InnerReadOperation(
      targetField = AliasedField(model.primaryField, None, Vector.empty),
      targetModel = model,
      user = None,
      hooks = Vector.empty,
      innerReadOps = Vector.empty
    )
}
