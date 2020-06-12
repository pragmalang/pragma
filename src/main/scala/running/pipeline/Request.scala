package running.pipeline

import spray.json._
import running.JwtPaylod
import domain._, utils.InternalException
import sangria.ast._

case class Request(
    hookData: Option[JsValue],
    body: Option[JsObject],
    user: Option[JwtPaylod],
    query: Document,
    queryVariables: Either[JsObject, Seq[JsObject]],
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
    opKind: Operations.OperationKind,
    gqlOpKind: Operations.GqlOperationType,
    opArguments: Vector[Argument],
    directives: Vector[sangria.ast.Directive],
    event: PEvent,
    targetModel: PModel,
    role: Option[PModel],
    user: Option[JwtPaylod],
    // Contains hooks used in @onRead, @onWrite, and @onDelete directives
    crudHooks: Seq[PFunctionValue[_, _]],
    alias: Option[String],
    innerReadOps: Vector[InnerOperation]
)

/** Represents a data read selection within within an operation */
case class InnerOperation(
    targetField: Operations.AliasedField,
    operation: Operation
) {
  val nameOrAlias = targetField.alias.getOrElse(targetField.field.id)

  def displayTargetResource =
    operation.targetModel.id + "." + targetField.field.id
}

/**
  This GraphQL query example might help explain how operations are generated:
  ```gql
  mutation {
    User { # This is a model-level directive
      create(...) { # This is an operation selection
        username # 1
        name # 2
        # 1, 2, and `todos` are model field selections
        # Model field selections are converted to `InnerOperation`s
        todos {
          title
          content
        }
      }
    }
  }
  ```
  */
object Operations {
  type OperationsMap = Map[Option[String], Vector[Operation]]

  sealed trait OperationKind
  case object ReadOperation extends OperationKind
  case object WriteOperation extends OperationKind
  case object DeleteOperation extends OperationKind

  case class AliasedField(
      field: PShapeField,
      alias: Option[String] = None,
      directives: Vector[sangria.ast.Directive]
  )
  type FieldSelection = Field
  type GqlOperationType = OperationType

  def from(request: Request)(implicit st: SyntaxTree) =
    request.query.operations.map {
      case (name, op) => {
        val modelSelections = op.selections.map {
          case f: FieldSelection => f
          case _ =>
            throw new InternalException(
              s"GraphQL query model selections must all be field selections. Something must've went wrond during query reduction"
            )
        }
        (
          name,
          modelSelections.flatMap(
            fromModelSelection(_, op.operationType, request.user, st)
          )
        )
      }
    }

  def opSelectionEvent(opSelection: String, model: PModel): PEvent =
    opSelection match {
      case "read"       => Read
      case "list"       => ReadMany
      case "create"     => Create
      case "createMany" => CreateMany
      case "update"     => Update
      case "updateMany" => UpdateMany
      case "delete"     => Delete
      case "deleteMany" => DeleteMany
      case "login"      => Login
      case _ if opSelection startsWith "pushTo" =>
        PushTo(captureListField(model, opSelection.replace("pushTo", "")))
      case _ if opSelection startsWith "pushManyTo" =>
        PushManyTo(
          captureListField(model, opSelection.replace("pushManyTo", ""))
        )
      case _ if opSelection startsWith "removeFrom" =>
        RemoveFrom(
          captureListField(model, opSelection.replace("removeFrom", ""))
        )
      case _ if opSelection startsWith "removeManyFrom" =>
        RemoveManyFrom(
          captureListField(model, opSelection.replace("removeManyFrom", ""))
        )
    }

  def opKindFromEvent(event: PEvent): Operations.OperationKind = event match {
    case Read | ReadMany => ReadOperation
    case Create | Update | PushTo(_) | PushManyTo(_) | RemoveFrom(_) |
        RemoveManyFrom(_) =>
      WriteOperation
    case Delete => DeleteOperation
    case _      => throw new InternalException(s"Invalid operation event `$event`")
  }

  def captureListField(
      model: PModel,
      capturedFieldName: String
  ): PModelField = {
    if (model.fields
          .filter(
            _.id.toLowerCase == capturedFieldName.toLowerCase
          )
          .length == 1) {
      model.fields.find(_.id.toLowerCase == capturedFieldName.toLowerCase).get
    } else {
      model.fields.find(_.id == capturedFieldName).get
    }
  }

  def fromModelSelection(
      modelSelection: FieldSelection,
      gqlOpKind: GqlOperationType,
      user: Option[JwtPaylod],
      st: SyntaxTree
  ): Vector[Operation] = {
    val targetModel = st.modelsById.get(modelSelection.name) match {
      case Some(model) => model
      case _ =>
        throw new InternalException(
          "Requested model is not defined. Something must've went wrong during query validation against the schema"
        )
    }
    val userRole = user.flatMap { jwt =>
      st.modelsById.get(jwt.role)
    }
    modelSelection.selections.map {
      case opSelection: FieldSelection =>
        fromOperationSelection(
          targetModel,
          gqlOpKind,
          opSelection,
          userRole,
          user,
          modelSelection.directives,
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
      gqlOpKind: GqlOperationType,
      opSelection: FieldSelection,
      role: Option[PModel],
      user: Option[JwtPaylod],
      modelLevelDirectives: Vector[sangria.ast.Directive],
      st: SyntaxTree
  ): Operation = {
    val event = opSelectionEvent(opSelection.name, model)
    val innerOps = opSelection.selections.map {
      case modelFieldSelection: FieldSelection =>
        innerOpFromModelFieldSelection(
          modelFieldSelection,
          model,
          gqlOpKind,
          role,
          user,
          st
        )
      case s =>
        throw new InternalException(
          s"Selection `${s.renderCompact}` is not a field selection. All selections inside a model selection must all be field selections. Something must've went wrong during query reduction"
        )
    }
    Operation(
      opKind = opKindFromEvent(event),
      gqlOpKind = gqlOpKind,
      opArguments = opSelection.arguments,
      directives = opSelection.directives,
      event = event,
      targetModel = model,
      role = role,
      user = user,
      crudHooks = model.readHooks,
      alias = opSelection.alias,
      innerReadOps = innerOps
    )
  }

  def innerOpFromModelFieldSelection(
      modelFieldSelection: FieldSelection,
      outerTargetModel: PModel,
      gqlOpKind: GqlOperationType,
      role: Option[PModel],
      user: Option[JwtPaylod],
      st: SyntaxTree
  ): InnerOperation = {
    val targetField =
      outerTargetModel.fieldsById.get(modelFieldSelection.name) match {
        case Some(field: PModelField) => field
        case _ =>
          throw new InternalException(
            s"Requested field `${modelFieldSelection.name}` of model `${outerTargetModel.id}` is not defined. Something must've went wrong during query validation"
          )
      }
    val targetFieldType = targetField.ptype match {
      case m: PReference                  => st.findTypeById(m.id)
      case PArray(m: PReference)          => st.findTypeById(m.id)
      case POption(m: PReference)         => st.findTypeById(m.id)
      case POption(PArray(m: PReference)) => st.findTypeById(m.id)
      case p                              => Some(p)
    }
    val innerOpTargetModel = targetFieldType match {
      case Some(m: PModel) => m
      case Some(_: PrimitiveType) | Some(_: PEnum)
          if !modelFieldSelection.selections.isEmpty =>
        throw new InternalException(
          s"Field `${targetField.id}` is not of a model type (it cannot have inner sellections in a GraphQL query). Something must've went wrong during query validation"
        )
      case None =>
        throw new InternalException(
          s"Inner operation targets model `${targetField.ptype.toString}` that doesn't exist"
        )
      case _ => outerTargetModel
    }
    val innerSelections = modelFieldSelection.selections.map {
      case f: FieldSelection =>
        innerOpFromModelFieldSelection(
          f,
          innerOpTargetModel,
          gqlOpKind,
          role,
          user,
          st
        )
      case _ =>
        throw new InternalException(
          s"Selection `${modelFieldSelection.name}` is not a field selection. All selections within operation selection must be field selections. Something must've went wrong during GraphQL query validation"
        )
    }
    val op = Operation(
      opKind = Operations.ReadOperation,
      gqlOpKind = OperationType.Query,
      opArguments = Vector.empty,
      directives = modelFieldSelection.directives,
      event = Read,
      targetModel = innerOpTargetModel,
      role = role,
      user = user,
      crudHooks = innerOpTargetModel.readHooks,
      alias = modelFieldSelection.alias,
      innerReadOps = innerSelections
    )
    InnerOperation(
      AliasedField(
        targetField,
        modelFieldSelection.alias,
        modelFieldSelection.directives
      ),
      op
    )
  }

}
