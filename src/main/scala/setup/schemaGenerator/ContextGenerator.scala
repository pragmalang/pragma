package setup.schemaGenerator

import domain._
import domain.primitives.ExternalFunction
import sangria.ast.OperationType

case class ContextGenerator(st: SyntaxTree) {
  def operation(
      opType: OperationType,
      modelName: String,
      opName: String
  ): Operation =
    opType match {
      case _ => ???
    }
}

trait Operation {
  val event: HEvent
  val model: HModel
  // Contains hooks used in @onRead, @onWrite, and @onDelete directives
  val crudHooks: List[ExternalFunction]
  val authHooks: List[ExternalFunction]
}

case class ReadOperation(
    event: HEvent,
    model: HModel,
    user: HModel,
    crudHooks: List[ExternalFunction] = Nil,
    authHooks: List[ExternalFunction] = Nil
) extends Operation

case class WriteOperation(
    event: HEvent,
    model: HModel,
    crudHooks: List[ExternalFunction] = Nil,
    authHooks: List[ExternalFunction] = Nil
) extends Operation

case class DeleteOperation(
    event: HEvent,
    model: HModel,
    crudHooks: List[ExternalFunction] = Nil,
    authHooks: List[ExternalFunction] = Nil
) extends Operation
