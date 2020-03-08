package running.pipeline

import spray.json._
import running.JwtPaylod
import domain._
import domain.primitives.ExternalFunction
import sangria.ast._
import Operation._

case class Request(
    hookData: Option[JsValue],
    body: Option[JsObject],
    user: Option[JwtPaylod],
    query: Document,
    queryVariables: Either[JsObject, List[JsObject]],
    cookies: Map[String, String],
    url: String,
    hostname: String
) extends PipelineInput
    with PipelineOutput {
  // List[Operation] -- TBC
  lazy val operations = query.operations.flatMap {
    case (_, op) => Request.operationsFrom(op.operationType, op.selections)
  }
}

object Request {
  def operationsFrom(
      operationType: GqlOperationType,
      selections: Vector[Selection]
  ) =
    for {
      selection <- selections
      // TBD
    } yield "an operation"

  lazy val hType: HInterface = HInterface(
    "Request",
    List(
      HInterfaceField("hookData", HAny, None),
      HInterfaceField("body", HAny, None),
      HInterfaceField("user", HAny, None),
      HInterfaceField("query", HAny, None),
      HInterfaceField("queryVariable", HAny, None),
      HInterfaceField("cookies", HAny, None),
      HInterfaceField("url", HAny, None),
      HInterfaceField("hostname", HAny, None)
    ),
    None
  )
}

trait Operation {
  val arguments: Map[String, JsValue]
  val selections: List[FieldSelection]
  val event: HEvent
  val resource: Resource
  val role: Option[HModel]
  val user: Option[JwtPaylod]
  // Contains hooks used in @onRead, @onWrite, and @onDelete directives
  val crudHooks: List[ExternalFunction]
  val authHooks: List[ExternalFunction]
}
object Operation {
  type FieldSelection = Field
  type GqlOperationType = OperationType

  def operationEvent(opName: String): HEvent = opName match {
    case "read"                              => Read
    case "list"                              => ReadMany
    case "create"                            => Create
    case "update"                            => Update
    case "mutate"                            => Mutate
    case "delete"                            => Delete
    case "recover"                           => Recover
    case _ if opName startsWith "pushTo"     => PushTo
    case _ if opName startsWith "deleteFrom" => DeleteFrom
  }
}

case class ReadOperation(
    arguments: Map[String, JsValue] = Map.empty,
    selections: List[FieldSelection] = Nil,
    event: HEvent,
    resource: Resource,
    role: Option[HModel] = None,
    user: Option[JwtPaylod] = None,
    crudHooks: List[ExternalFunction] = Nil,
    authHooks: List[ExternalFunction] = Nil
) extends Operation

case class WriteOperation(
    arguments: Map[String, JsValue] = Map.empty,
    selections: List[FieldSelection] = Nil,
    event: HEvent,
    resource: Resource,
    role: Option[HModel] = None,
    user: Option[JwtPaylod] = None,
    crudHooks: List[ExternalFunction] = Nil,
    authHooks: List[ExternalFunction] = Nil
) extends Operation

case class DeleteOperation(
    arguments: Map[String, JsValue] = Map.empty,
    selections: List[FieldSelection] = Nil,
    event: HEvent,
    resource: Resource,
    role: Option[HModel] = None,
    user: Option[JwtPaylod] = None,
    crudHooks: List[ExternalFunction] = Nil,
    authHooks: List[ExternalFunction] = Nil
) extends Operation
