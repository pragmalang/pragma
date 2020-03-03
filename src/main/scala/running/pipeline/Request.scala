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
  lazy val operations: List[Operation] = ??? // TODO: Tabzz98
}

object Request {
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
  val model: HModel
  val role: Option[HModel]
  val user: Option[JwtPaylod]
  // Contains hooks used in @onRead, @onWrite, and @onDelete directives
  val crudHooks: List[ExternalFunction]
  val authHooks: List[ExternalFunction]
}
object Operation {
  type FieldSelection = Field
}

case class ReadOperation(
    arguments: Map[String, JsValue] = Map.empty,
    selections: List[FieldSelection] = Nil,
    event: HEvent,
    model: HModel,
    role: Option[HModel] = None,
    user: Option[JwtPaylod] = None,
    crudHooks: List[ExternalFunction] = Nil,
    authHooks: List[ExternalFunction] = Nil
) extends Operation

case class WriteOperation(
    arguments: Map[String, JsValue] = Map.empty,
    selections: List[FieldSelection] = Nil,
    event: HEvent,
    model: HModel,
    role: Option[HModel] = None,
    user: Option[JwtPaylod] = None,
    crudHooks: List[ExternalFunction] = Nil,
    authHooks: List[ExternalFunction] = Nil
) extends Operation

case class DeleteOperation(
    arguments: Map[String, JsValue] = Map.empty,
    selections: List[FieldSelection] = Nil,
    event: HEvent,
    model: HModel,
    role: Option[HModel] = None,
    user: Option[JwtPaylod] = None,
    crudHooks: List[ExternalFunction] = Nil,
    authHooks: List[ExternalFunction] = Nil
) extends Operation
