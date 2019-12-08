package running.pipeline

import running._
import spray.json.{JsObject, JsString, JsValue}
import domain._
import domain.primitives._
import sangria.ast._
import scala.util.Try

case class Request(
    data: Option[JsValue],
    ctx: RequestContext,
    body: Option[JsObject],
    syntaxTree: Option[SyntaxTree] = None
) extends PipelineInput
    with PipelineOutput {
  lazy val resource: Try[List[Resource]] = Try {
    val opDef = ctx.graphQlQuery.definitions
      .find(_.isInstanceOf[OperationDefinition])
      .get
      .asInstanceOf[OperationDefinition]
    val selectedModels = syntaxTree.get.models.filter { model =>
      opDef.selections.exists({
        case s: Field => s.name.toLowerCase == model.id.toLowerCase
        case _        => false
      })
    }

    val selectedFields = ???
    ???
  }
}

case class Resource(
    operation: HEvent,
    model: HModel,
    selectedFields: List[SelectedField]
) {
  lazy val fieldsHooks =
    selectedFields.map(_.field).map(f => f.id -> f.hooks).toMap
}

case class SelectedField(
    field: HModelField,
    selectedFields: List[SelectedField]
) {
  lazy val canHaveSelection = field.htype match {
    case HReference(_) => true
    case HSelf(_)      => true
    case _: HShape     => true
    case _             => false
  }

  lazy val fieldType = field.htype
}
