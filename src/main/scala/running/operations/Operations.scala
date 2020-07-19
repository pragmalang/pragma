package running

import domain._, domain.utils._, domain.DomainImplicits._
import sangria.ast._
import spray.json._
import cats.implicits._
import running.storage._, running.utils._

object Operations {
  type OperationsMap = Map[Option[String], Vector[Operation]]

  case class AliasedField(
      field: PShapeField,
      alias: Option[String] = None,
      directives: Vector[sangria.ast.Directive]
  )
  type FieldSelection = Field
  type GqlOperationType = OperationType

  /**
  This GraphQL query example can help explain how operations are generated:
  ```gql
  mutation {
    User { # This is a model-level directive
      create(...) { # This is an operation selection
        #     ^   Arguments are parsed into `OpArgs` and stored in `Operation#opArguments`
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
  def from(
      request: Request
  )(implicit st: SyntaxTree): Either[Exception, OperationsMap] =
    request.query.operations.toVector
      .traverse {
        case (name, op) => {
          val modelSelections = op.selections.flatTraverse {
            case f: FieldSelection =>
              fromModelSelection(f, request.user, st)
            case _ =>
              Left {
                InternalException(
                  s"GraphQL query model selections must all be field selections. Something must've went wrond during query reduction"
                )
              }
          }
          modelSelections.map(name -> _)
        }
      }
      .map(_.toMap)

  private def opSelectionEvent(opSelection: String, model: PModel): PEvent =
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
      case s if s startsWith "pushTo" =>
        PushTo(captureListField(model, s.replace("pushTo", "")))
      case s if s startsWith "pushManyTo" =>
        PushManyTo(captureListField(model, s.replace("pushManyTo", "")))
      case s if s startsWith "removeFrom" =>
        RemoveFrom(captureListField(model, s.replace("removeFrom", "")))
      case s if s startsWith "removeManyFrom" =>
        RemoveManyFrom(captureListField(model, s.replace("removeManyFrom", "")))
    }

  private def captureListField(
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

  private def fromModelSelection(
      modelSelection: FieldSelection,
      user: Option[JwtPayload],
      st: SyntaxTree
  ): Either[Exception, Vector[Operation]] = {
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
    modelSelection.selections.traverse {
      case opSelection: FieldSelection =>
        fromOperationSelection(
          targetModel,
          opSelection,
          userRole,
          user,
          st
        )
      case _ =>
        InternalException(
          "GraphQL query should only contain field selections. Something mus've went wrong during query reduction"
        ).asLeft
    }
  }

  private def fromOperationSelection(
      model: PModel,
      opSelection: FieldSelection,
      role: Option[PModel],
      user: Option[JwtPayload],
      st: SyntaxTree
  ): Either[Exception, Operation] = {
    val event = opSelectionEvent(opSelection.name, model)
    val innerOps = opSelection.selections.traverse {
      case modelFieldSelection: FieldSelection =>
        innerOpFromModelFieldSelection(
          modelFieldSelection,
          model,
          role,
          user,
          st
        )
      case s =>
        InternalException(
          s"Selection `${s.renderCompact}` is not a field selection. All selections inside a model selection must all be field selections. Something must've went wrong during query reduction"
        ).asLeft
    }
    val opArgs = parseArgs(event, model, opSelection)
    for {
      args <- opArgs
      iops <- innerOps
      op <- (event, args) match {
        case (Read, as: ReadArgs) =>
          Right {
            ReadOperation(
              as,
              model,
              user zip role,
              model.readHooks,
              opSelection.alias,
              iops
            )
          }
        case (ReadMany, as: ReadManyArgs) =>
          Right {
            ReadManyOperation(
              as,
              model,
              user zip role,
              model.readHooks,
              opSelection.alias,
              iops
            )
          }
        case (Create, as: CreateArgs) =>
          Right {
            CreateOperation(
              as,
              model,
              user zip role,
              model.writeHooks,
              opSelection.alias,
              iops
            )
          }
        case (CreateMany, as: CreateManyArgs) =>
          Right {
            CreateManyOperation(
              as,
              model,
              user zip role,
              model.writeHooks,
              opSelection.alias,
              iops
            )
          }
        case (Update, as: UpdateArgs) =>
          Right {
            UpdateOperation(
              as,
              model,
              user zip role,
              model.writeHooks,
              opSelection.alias,
              iops
            )
          }
        case (UpdateMany, as: UpdateManyArgs) =>
          Right {
            UpdateManyOperation(
              as,
              model,
              user zip role,
              model.writeHooks,
              opSelection.alias,
              iops
            )
          }
        case (domain.Delete, as: DeleteArgs) =>
          Right {
            DeleteOperation(
              as,
              model,
              user zip role,
              model.writeHooks,
              opSelection.alias,
              iops
            )
          }
        case (DeleteMany, as: DeleteManyArgs) =>
          Right {
            DeleteManyOperation(
              as,
              model,
              user zip role,
              model.writeHooks,
              opSelection.alias,
              iops
            )
          }
        case (PushTo(arrayField), as: PushToArgs) =>
          Right {
            PushToOperation(
              as,
              model,
              user zip role,
              model.writeHooks,
              opSelection.alias,
              iops,
              arrayField
            )
          }
        case (PushManyTo(arrayField), as: PushManyToArgs) =>
          Right {
            PushManyToOperation(
              as,
              model,
              user zip role,
              model.writeHooks,
              opSelection.alias,
              iops,
              arrayField
            )
          }
        case (RemoveFrom(arrayField), as: RemoveFromArgs) =>
          Right {
            RemoveFromOperation(
              as,
              model,
              user zip role,
              model.writeHooks,
              opSelection.alias,
              iops,
              arrayField
            )
          }
        case (RemoveManyFrom(arrayField), as: RemoveManyFromArgs) =>
          Right {
            RemoveManyFromOperation(
              as,
              model,
              user zip role,
              model.writeHooks,
              opSelection.alias,
              iops,
              arrayField
            )
          }
        case (Login, as: LoginArgs) =>
          Right {
            LoginOperation(
              as,
              model,
              user zip role,
              model.writeHooks,
              opSelection.alias,
              iops
            )
          }
        case (ev, _) =>
          Left {
            InternalException(s"Invalid `$ev` operation arguments")
          }
      }
    } yield op
  }

  private def innerOpFromModelFieldSelection(
      modelFieldSelection: FieldSelection,
      outerTargetModel: PModel,
      role: Option[PModel],
      user: Option[JwtPayload],
      st: SyntaxTree
  ): Either[Exception, InnerOperation] = {
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
    val innerSelections = modelFieldSelection.selections.traverse {
      case f: FieldSelection =>
        innerOpFromModelFieldSelection(
          f,
          innerOpTargetModel,
          role,
          user,
          st
        )
      case _ =>
        throw new InternalException(
          s"Selection `${modelFieldSelection.name}` is not a field selection. All selections within operation selection must be field selections. Something must've went wrong during GraphQL query validation"
        )
    }
    val iopArgs = parseInnerOpArgs(outerTargetModel, modelFieldSelection)(st)
    for {
      innerSels <- innerSelections
      args <- iopArgs
      aliasedField = AliasedField(
        targetField,
        modelFieldSelection.alias,
        modelFieldSelection.directives
      )
    } yield
      args match {
        case InnerOpNoArgs =>
          InnerReadOperation(
            targetField = aliasedField,
            targetModel = innerOpTargetModel,
            user = user zip role,
            crudHooks = innerOpTargetModel.readHooks,
            innerReadOps = innerSels
          )
        case as: InnerListArgs =>
          InnerReadManyOperation(
            targetField = aliasedField,
            opArguments = as,
            targetModel = innerOpTargetModel,
            user = user zip role,
            crudHooks = innerOpTargetModel.readHooks,
            innerReadOps = innerSels
          )
      }
  }

  /** Parses GraphQL arguments into `OpArgs` of the appropriate type */
  private def parseArgs(
      event: PEvent,
      opTargetModel: PModel,
      opSelection: FieldSelection
  ): Either[Exception, OpArgs[PEvent]] =
    event match {
      case domain.Read => {
        val id =
          opSelection.arguments
            .find(_.name == opTargetModel.primaryField.id)
            .map(_.value)
        id match {
          case Some(value) => ReadArgs(sangriaToJson(value)).asRight
          case _ =>
            UserError(
              "Arguments of Read operation should contain the ID of the record to read"
            ).asLeft
        }
      }
      case ReadMany => {
        // TODO: Parse QueryWhere in Operations
        val where = QueryWhere(None, None, None)
        ReadManyArgs(Some(where)).asRight
      }
      case Create => {
        val objToInsert =
          objFieldsFrom(opSelection.arguments)
            .find(_._1 == opTargetModel.id.small)
            .map(_._2.asJsObject)

        objToInsert match {
          case Some(obj) => CreateArgs(obj).asRight
          case _ =>
            UserError(
              s"Create mutation takes a `${opTargetModel.id.small}` argument"
            ).asLeft
        }
      }
      case CreateMany => {
        val records =
          objFieldsFrom(opSelection.arguments)
            .find(_._1 == "items")
            .map {
              case (_, arr: JsArray) =>
                arr.elements.map {
                  case obj: JsObject => obj
                  case nonObj =>
                    throw InternalException(
                      s"Trying to create a record with non-object value `$nonObj`"
                    )
                }
              case _ =>
                throw InternalException("Value `items` must be an array")
            }
            .getOrElse(
              throw InternalException(
                "CREATE_MANY operation arguments must have an `items` field"
              )
            )
        CreateManyArgs(records).asRight
      }
      case PushTo(_) => {
        val item = opSelection.arguments
          .find(_.name == "item")
          .map(arg => sangriaToJson(arg.value))
          .getOrElse(
            throw InternalException {
              "Arguments of `PUSH_TO` operation must contain the item to be pushed"
            }
          )
        val sourceId = opSelection.arguments
          .find(_.name == opTargetModel.primaryField.id)
          .map(arg => sangriaToJson(arg.value))
          .getOrElse(
            throw InternalException {
              "Arguments of `PUSH_TO` operation must contain the ID of the array field object"
            }
          )
        PushToArgs(sourceId, item).asRight
      }
      case PushManyTo(_) => {
        val items = opSelection.arguments
          .find(_.name == "items")
          .map(_.value)
          .map {
            case ls: sangria.ast.ListValue => ls.values.map(sangriaToJson)
            case _ =>
              throw InternalException(
                "`items` argument of `PUSH_MANY_TO` operation must be an array"
              )
          }
          .getOrElse(
            throw InternalException {
              "Arguments of `PUSH_MANY_TO` operation must contain the items to be pushed"
            }
          )
        val sourceId = opSelection.arguments
          .find(_.name == opTargetModel.primaryField.id)
          .map(arg => sangriaToJson(arg.value))
          .getOrElse(
            throw InternalException {
              "Arguments of `PUSH_TO` operation must contain the ID of the array field object"
            }
          )
        PushManyToArgs(sourceId, items).asRight
      }
      case Delete => {
        val id = opSelection.arguments
          .collectFirst {
            case arg if arg.name == opTargetModel.primaryField.id =>
              sangriaToJson(arg.value)
          }
          .getOrElse {
            throw new InternalException(
              "DELETE operation arguments must contain the ID of the record to be deleted"
            )
          }
        DeleteArgs(id).asRight
      }
      case DeleteMany => {
        val idsToDelete = opSelection.arguments.collectFirst {
          case arg if arg.name == "items" => sangriaToJson(arg.value)
        }
        idsToDelete match {
          case Some(arr: JsArray) => DeleteManyArgs(arr.elements).asRight
          case _ =>
            UserError(
              "DELETE_MANY operation must have an `items` list argument"
            ).asLeft
        }
      }
      case RemoveFrom(_) => {
        val sourceId = opSelection.arguments
          .collectFirst {
            case arg if arg.name == opTargetModel.primaryField.id =>
              sangriaToJson(arg.value)
          }
          .getOrElse {
            throw InternalException(
              "REMOVE_FROM operation arguments must contain the ID of the parent object"
            )
          }
        val targetId = opSelection.arguments
          .collectFirst {
            case arg if arg.name == "item" =>
              sangriaToJson(arg.value)
          }
          .getOrElse {
            throw InternalException(
              "REMOVE_FROM operation arguments must contain the ID of the child object to remove"
            )
          }
        RemoveFromArgs(sourceId, targetId).asRight
      }
      case RemoveManyFrom(_) => ???
      case domain.Update => {
        val objId = opSelection.arguments.collectFirst {
          case arg if arg.name == opTargetModel.primaryField.id =>
            sangriaToJson(arg.value)
        }
        val data = opSelection.arguments.collectFirst {
          case arg if arg.name == "data" => sangriaToJson(arg.value)
        }
        (objId, data) match {
          case (Some(id), Some(data: JsObject)) =>
            UpdateArgs(ObjectWithId(data, id)).asRight
          case (None, _) =>
            UserError(
              s"UPDATE arguments must contain the ID of the object to be updated"
            ).asLeft
          case (_, None) =>
            UserError(
              s"UPDATE arguments must contain a `data` object"
            ).asLeft
          case _ =>
            UserError(
              s"UPDATE arguments must contain an ID and a `data` object"
            ).asLeft
        }
      }
      case UpdateMany => ???
      case Login      => ???
    }

  private def parseInnerOpArgs(
      opTargetModel: PModel,
      fieldSelection: FieldSelection
  )(implicit st: SyntaxTree): Either[Exception, InnerOpArgs[ReadEvent]] = {
    val modelField = opTargetModel.fieldsById.get(fieldSelection.name)
    modelField match {
      case None =>
        new Exception(
          s"`${fieldSelection.name}` is not field of model `${opTargetModel.id}`"
        ).asLeft
      case Some(PModelField(_, PArray(PReference(refId)), _, _, _, _))
          if st.modelsById.contains(refId) =>
        InnerListArgs(None).asRight // TODO: Parse `QueryWhere` from JSON
      case _ => InnerOpNoArgs.asRight
    }
  }

  /** Utility function to get an inner operation
    * to read the primary field of a model.
    */
  def primaryFieldInnerOp(model: PModel): InnerOperation =
    InnerReadOperation(
      targetField = AliasedField(model.primaryField, None, Vector.empty),
      targetModel = model,
      user = None,
      crudHooks = Vector.empty,
      innerReadOps = Vector.empty
    )

}
