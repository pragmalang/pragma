package running.pipeline

import spray.json._
import running.JwtPaylod
import domain._, utils.InternalException
import sangria.ast._
import running.Implicits.GraphQlValueJsonFormater
import domain.primitives.`package`.PFunctionValue
import domain.primitives._

case class Request(
    hookData: Option[JsValue],
    body: Option[JsObject],
    user: Option[JwtPaylod],
    query: Document,
    queryVariables: Either[JsObject, List[JsObject]],
    cookies: Map[String, String],
    url: String,
    hostname: String
)

object Request {
  lazy val pType: PInterface = PInterface(
    "Request",
    List(
      PInterfaceField("hookData", PAny, None),
      PInterfaceField("body", PAny, None),
      PInterfaceField("user", PAny, None),
      PInterfaceField("query", PAny, None),
      PInterfaceField("queryVariable", PAny, None),
      PInterfaceField("cookies", PAny, None),
      PInterfaceField("url", PAny, None),
      PInterfaceField("hostname", PAny, None)
    ),
    None
  )
}

case class Operation(
    opKind: Operation.OperationKind,
    arguments: Map[String, JsValue],
    event: PEvent,
    targetModel: PModel,
    fieldPath: Operation.AliasedResourcePath,
    role: Option[PModel],
    user: Option[JwtPaylod],
    // Contains hooks used in @onRead, @onWrite, and @onDelete directives
    crudHooks: List[PFunctionValue[_, _]],
    authRules: List[AccessRule]
)
object Operation {
  sealed trait OperationKind
  case object ReadOperation extends OperationKind
  case object WriteOperation extends OperationKind
  case object DeleteOperation extends OperationKind

  case class AliasedField(field: PShapeField, alias: Option[String] = None)
  type AliasedResourcePath = List[AliasedField]
  type FieldSelection = Field
  type GqlOperationType = OperationType

  def operationsFrom(request: Request)(implicit st: SyntaxTree) =
    request.query.operations.map {
      case (name, op) => {
        val modelSelections = op.selections.map {
          case f: FieldSelection => f
          case _ =>
            throw new InternalException(
              s"GraphQL query model selections must all be field selections. Something must've went wrond during query reduction"
            )
        }
        (name, modelSelections.flatMap(fromModelSelection(_, request.user, st)))
      }
    }

  def opSelectionEvent(opSelection: String): PEvent =
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

  def fromModelSelection(
      modelSelection: FieldSelection,
      user: Option[JwtPaylod],
      st: SyntaxTree
  ): Vector[Operation] = {
    val targettedModel = st.models.find(_.id == modelSelection.name) match {
      case Some(model) => model
      case _ =>
        throw new InternalException(
          "Requested model is not defined. Something must've went wrong during query validation against the schema"
        )
    }
    val userRole = user.flatMap { jwt =>
      st.models.find(_.id == jwt.role)
    }
    modelSelection.selections.flatMap {
      case opSelection: FieldSelection =>
        fromOperationSelection(
          targettedModel,
          opSelection,
          userRole,
          user,
          st
        )
      case _ =>
        throw new InternalException(
          "GraphQL query should only contain field selections. Something mus've went wrong during query reduction"
        )
    }
  }

  def fromOperationSelection(
      model: PModel,
      opSelection: FieldSelection,
      role: Option[PModel],
      user: Option[JwtPaylod],
      st: SyntaxTree
  ): Vector[Operation] = {
    val event = opSelectionEvent(opSelection.name)
    val opArguments = opSelection.arguments.map { arg =>
      arg.name -> GraphQlValueJsonFormater.write(arg.value)
    }.toMap
    opSelection.selections.flatMap {
      case modelFieldSelection: FieldSelection =>
        fromModelFieldSelection(
          modelFieldSelection,
          opArguments,
          event,
          model,
          role,
          user,
          st
        )
      case s =>
        throw new InternalException(
          s"Selection `${s.renderCompact}` is not a field selection. All selections inside a model selection must all be field selections. Something must've went wrong during query reduction"
        )
    }
  }

  def fromModelFieldSelection(
      modelFieldSelection: FieldSelection,
      opArguments: Map[String, JsValue],
      event: PEvent,
      model: PModel,
      role: Option[PModel],
      user: Option[JwtPaylod],
      st: SyntaxTree,
      fieldPath: AliasedResourcePath = Nil
  ): Vector[Operation] = {
    val selectedModelField =
      model.fields.find(_.id == modelFieldSelection.name) match {
        case Some(field: PModelField) => field
        case _ =>
          throw new InternalException(
            s"Requested field `${modelFieldSelection.name}` of model `${model.id}` is not defined. Something must've went wrong during query validation"
          )
      }
    if (modelFieldSelection.selections.isEmpty) {
      val newPath = fieldPath :+ AliasedField(
        selectedModelField,
        modelFieldSelection.alias
      )
      val (crudHooks, kind) = event match {
        case Read | ReadMany => (model.readHooks, ReadOperation)
        case Create | Update | Mutate | PushTo | RemoveFrom =>
          (model.writeHooks, WriteOperation)
        case Delete => (model.deleteHooks, DeleteOperation)
        case _      => throw new InternalException(s"Invalid operation event $event")
      }
      val authRules = role.zip(st.permissions) match {
        case None => Nil
        case Some((role, permissions)) =>
          permissions.globalTenant.roles
            .find(_.user.id == role.id)
            .map(_.rules)
            .getOrElse(Nil)
      }
      Vector(
        Operation(
          kind,
          opArguments,
          event,
          model,
          newPath,
          role,
          user,
          crudHooks,
          authRules
        )
      )
    } else {
      val newPath = fieldPath :+ AliasedField(
        selectedModelField,
        modelFieldSelection.alias
      )
      val fieldModelId = selectedModelField.ptype match {
        case m: PReference          => m.id
        case PArray(m: PReference)  => m.id
        case POption(m: PReference) => m.id
        case _ =>
          throw new InternalException(
            s"Field `${selectedModelField.id}` is not of a model type (it cannot have inner sellections in a GraphQL query). Something must've went wrong during query validation"
          )
      }
      val fieldModel = st.models.find(_.id == fieldModelId) match {
        case Some(model: PModel) => model
        case None =>
          throw new InternalException(
            s"Requested model `${fieldModelId}` is not defined. Something must've went wrong during validation"
          )
      }
      modelFieldSelection.selections.flatMap {
        case f: FieldSelection =>
          fromModelFieldSelection(
            f,
            opArguments,
            event,
            fieldModel,
            role,
            user,
            st,
            newPath
          )
        case _ =>
          throw new InternalException(
            s"Selection `${modelFieldSelection.name}` is not a field selection. All selections within operation selection must be field selections. Something must've went wrong during GraphQL query validation"
          )
      }
    }
  }

  def displayOpResource(op: Operation): String =
    op.targetModel.id + op.fieldPath.foldLeft(".")(_ + _.field.id)

}
