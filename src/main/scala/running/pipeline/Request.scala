package running.pipeline

import spray.json._
import running.JwtPaylod
import domain._
import domain.primitives.ExternalFunction
import sangria.ast._

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
    with PipelineOutput

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
/*
{
  User {
    read(username: "ljfn") {
      username
      friend {
        username
        friend {
          username
        }
      }
    }
  }
}
*/

trait Operation {
  val arguments: Map[String, JsValue]
  val event: HEvent
  // The affected resource (e.g. model or model field)
  val resource: ResourcePath
  val role: Option[HModel]
  val user: Option[JwtPaylod]
  // Contains hooks used in @onRead, @onWrite, and @onDelete directives
  val crudHooks: List[ExternalFunction]
  val authHooks: List[ExternalFunction]
}
object Operation {
  type FieldSelection = Field
  type GqlOperationType = OperationType

  def operationsFrom(gqlQuery: Document)(implicit st: SyntaxTree) = ???

  def opSelectionEvent(opSelection: String): HEvent =
    opSelection match {
      case "read"                                   => Read
      case "list"                                   => ReadMany
      case "create"                                 => Create
      case "update"                                 => Update
      case "mutate"                                 => Mutate
      case "delete"                                 => Delete
      case "recover"                                => Recover
      case _ if opSelection startsWith "pushTo"     => PushTo
      case _ if opSelection startsWith "deleteFrom" => RemoveFrom
    }

  def fromModelSelections(
      modelSelections: Vector[FieldSelection],
      user: Option[JwtPaylod],
      st: SyntaxTree
  ) =
    for {
      modelSelection: FieldSelection <- modelSelections
      event = opSelectionEvent(modelSelection.name)
      arguments = modelSelection.arguments
      targettedModel <- st.models.find(_.id == modelSelection.name)
      baseResource = ModelResource(targettedModel, None)
    } yield ???

  def fromOperationSelections(
      targetResource: ResourcePath,
      event: HEvent,
      opSelectionFields: Vector[FieldSelection]
  ) =
    if (opSelectionFields.isEmpty) Nil
    else {
      // val modelFieldSelection = opSelectionFields.head

      ???
    }

  def fromModelFieldSelections(
      modelFieldSelections: Vector[FieldSelection],
      opArguments: Map[String, JsValue],
      event: HEvent,
      resource: ResourcePath,
      role: Option[HModel],
      crudHooks: List[ExternalFunction],
      authHooks: List[ExternalFunction]
  ) = ???

}

case class ReadOperation(
    arguments: Map[String, JsValue] = Map.empty,
    event: HEvent,
    resource: ResourcePath,
    role: Option[HModel] = None,
    user: Option[JwtPaylod] = None,
    crudHooks: List[ExternalFunction] = Nil,
    authHooks: List[ExternalFunction] = Nil
) extends Operation

case class WriteOperation(
    arguments: Map[String, JsValue] = Map.empty,
    event: HEvent,
    resource: ResourcePath,
    role: Option[HModel] = None,
    user: Option[JwtPaylod] = None,
    crudHooks: List[ExternalFunction] = Nil,
    authHooks: List[ExternalFunction] = Nil
) extends Operation

case class DeleteOperation(
    arguments: Map[String, JsValue] = Map.empty,
    event: HEvent,
    resource: ResourcePath,
    role: Option[HModel] = None,
    user: Option[JwtPaylod] = None,
    crudHooks: List[ExternalFunction] = Nil,
    authHooks: List[ExternalFunction] = Nil
) extends Operation
