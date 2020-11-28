package running.operations

import pragma.domain._, pragma.domain.utils._, DomainImplicits._
import spray.json._
import cats.implicits._
import running._, running.utils._
import running.operations._, Operations._
import pragma.jwtUtils._

class OperationParser(st: SyntaxTree) {

  /** This GraphQL query example can help explain how operations are generated:
    * ```gql
    * mutation {
    *   User { # This is a model-level selection.
    *          # `User` is the `groupName` of the operations within its scope
    *          #  (the alias becomes the `groupName` if defined.)
    *     create(...) { # This is an operation selection
    *       #     ^   Arguments are parsed into `OpArgs` and stored in `Operation#opArguments`
    *       # `create` is the operation's `name` (if an alias is defined, it becomes the `name`.)
    *       username # 1
    *       name # 2
    *       # 1, 2, and `todos` are model field selections
    *       # Model field selections are converted to `InnerOperation`s
    *       todos {
    *         title
    *         content
    *       }
    *     }
    *   }
    * }
    * ```
    *
    * See these links for GraphQL spec conformance:
    * - https://spec.graphql.org/June2018/#sec-Response-Format
    * - https://spec.graphql.org/June2018/#sec-Field-Alias
    */
  def parse(
      request: Request
  ): Either[Exception, Operations.OperationsMap] =
    request.query.operations.toVector
      .traverse {
        case (name, op) => {
          val modelSelections = op.selections
            .traverse {
              case f: FieldSelection =>
                fromModelSelection(f, request.user, st).map(
                  f.alias.getOrElse(f.name) -> _
                )
              case _ =>
                Left {
                  InternalException(
                    s"GraphQL query model selections must all be field selections. Something must've went wrond during query reduction"
                  )
                }
            }
            .map(_.toMap)
          modelSelections.map(name -> _)
        }
      }
      .map(_.toMap)

  private def opSelectionEvent(opSelection: String, model: PModel): PEvent =
    opSelection match {
      case "read"                      => Read
      case "list"                      => ReadMany
      case "create"                    => Create
      case "createMany"                => CreateMany
      case "update"                    => Update
      case "updateMany"                => UpdateMany
      case "delete"                    => Delete
      case "deleteMany"                => DeleteMany
      case s if s startsWith "loginBy" => Login
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
    val opsGroupName = modelSelection.alias.getOrElse(targetModel.id)
    modelSelection.selections.traverse {
      case opSelection: FieldSelection =>
        fromOperationSelection(
          targetModel,
          opsGroupName,
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
      opGroupName: String,
      opSelection: FieldSelection,
      role: Option[PModel],
      user: Option[JwtPayload],
      st: SyntaxTree
  ): Either[Exception, Operation] = {
    val event = opSelectionEvent(opSelection.name, model)
    def innerOps(outputType: PType): Either[Exception, Vector[InnerOperation]] =
      opSelection.selections.flatTraverse {
        case modelFieldSelection: FieldSelection =>
          outputType match {
            case PReference(id) =>
              innerOpFromModelFieldSelection(
                modelFieldSelection,
                st.modelsById(id),
                role,
                user,
                st
              ).map(Vector(_))
            case PArray(PReference(id)) =>
              innerOpFromModelFieldSelection(
                modelFieldSelection,
                st.modelsById(id),
                role,
                user,
                st
              ).map(Vector(_))
            case POption(PReference(id)) =>
              innerOpFromModelFieldSelection(
                modelFieldSelection,
                st.modelsById(id),
                role,
                user,
                st
              ).map(Vector(_))
            case _ => Vector.empty.asRight
          }
        case s =>
          InternalException(
            s"Selection `${s.renderCompact}` is not a field selection. All selections inside a model selection must all be field selections. Something must've went wrong during query reduction"
          ).asLeft
      }
    val opName = opSelection.alias.getOrElse(opSelection.name)
    val opArgs = parseArgs(event, model, opSelection)
    for {
      args <- opArgs
      op <- (event, args) match {
        case (Read, as: ReadArgs) =>
          innerOps(model) map { iops =>
            ReadOperation(
              as,
              model,
              user zip role,
              model.readHooks,
              opName,
              opGroupName,
              iops
            )
          }
        case (ReadMany, as: ReadManyArgs) =>
          innerOps(model) map { iops =>
            ReadManyOperation(
              as,
              model,
              user zip role,
              model.readHooks,
              opName,
              opGroupName,
              iops
            )
          }
        case (Create, as: CreateArgs) =>
          innerOps(model) map { iops =>
            CreateOperation(
              as,
              model,
              user zip role,
              model.writeHooks,
              opName,
              opGroupName,
              iops
            )
          }
        case (CreateMany, as: CreateManyArgs) =>
          innerOps(model) map { iops =>
            CreateManyOperation(
              as,
              model,
              user zip role,
              model.writeHooks,
              opName,
              opGroupName,
              iops
            )
          }
        case (Update, as: UpdateArgs) =>
          innerOps(model) map { iops =>
            UpdateOperation(
              as,
              model,
              user zip role,
              model.writeHooks,
              opName,
              opGroupName,
              iops
            )
          }
        case (UpdateMany, as: UpdateManyArgs) =>
          innerOps(model) map { iops =>
            UpdateManyOperation(
              as,
              model,
              user zip role,
              model.writeHooks,
              opName,
              opGroupName,
              iops
            )
          }
        case (pragma.domain.Delete, as: DeleteArgs) =>
          innerOps(model) map { iops =>
            DeleteOperation(
              as,
              model,
              user zip role,
              model.deleteHooks,
              opName,
              opGroupName,
              iops
            )
          }
        case (DeleteMany, as: DeleteManyArgs) =>
          innerOps(model) map { iops =>
            DeleteManyOperation(
              as,
              model,
              user zip role,
              model.deleteHooks,
              opName,
              opGroupName,
              iops
            )
          }
        case (PushTo(arrayField), as: PushToArgs) =>
          innerOps(arrayField.ptype) map { iops =>
            PushToOperation(
              as,
              model,
              user zip role,
              model.writeHooks,
              opName,
              opGroupName,
              iops,
              model.fieldsById(arrayField.id)
            )
          }
        case (PushManyTo(arrayField), as: PushManyToArgs) =>
          innerOps(arrayField.ptype) map { iops =>
            PushManyToOperation(
              as,
              model,
              user zip role,
              model.writeHooks,
              opName,
              opGroupName,
              iops,
              model.fieldsById(arrayField.id)
            )
          }
        case (RemoveFrom(arrayField), as: RemoveFromArgs) =>
          innerOps(arrayField.ptype) map { iops =>
            RemoveFromOperation(
              as,
              model,
              user zip role,
              model.writeHooks,
              opName,
              opGroupName,
              iops,
              model.fieldsById(arrayField.id)
            )
          }
        case (RemoveManyFrom(arrayField), as: RemoveManyFromArgs) =>
          innerOps(arrayField.ptype) map { iops =>
            RemoveManyFromOperation(
              as,
              model,
              user zip role,
              model.writeHooks,
              opName,
              opGroupName,
              iops,
              model.fieldsById(arrayField.id)
            )
          }
        case (Login, as: LoginArgs) =>
          innerOps(model) map { iops =>
            LoginOperation(
              as,
              model,
              user zip role,
              model.loginHooks,
              opName,
              opGroupName,
              iops
            )
          }
        case (event, _) =>
          InternalException(s"Invalid `$event` operation arguments").asLeft
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
      case m: PReference          => st.findTypeById(m.id)
      case PArray(m: PReference)  => st.findTypeById(m.id)
      case POption(m: PReference) => st.findTypeById(m.id)
      case p                      => Some(p)
    }
    val innerOpTargetModel = targetFieldType match {
      case Some(m: PModel) => m
      case Some(_: PrimitiveType) | Some(_: PEnum) | Some(
            PArray(_: PrimitiveType)
          ) | Some(POption(_: PrimitiveType)) | Some(PArray(_: PEnum)) |
          Some(POption(_: PEnum)) if !modelFieldSelection.selections.isEmpty =>
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
    val iopArgs = parseInnerOpArgs(outerTargetModel, modelFieldSelection)
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
            hooks = innerOpTargetModel.readHooks,
            innerReadOps = innerSels
          )
        case as: InnerListArgs =>
          InnerReadManyOperation(
            targetField = aliasedField,
            opArguments = as,
            targetModel = innerOpTargetModel,
            user = user zip role,
            hooks = innerOpTargetModel.readHooks,
            innerReadOps = innerSels
          )
      }
  }

  private val aggParser = new QueryAggParser(st)

  /** Parses GraphQL arguments into `OpArgs` of the appropriate type */
  private def parseArgs(
      event: PEvent,
      opTargetModel: PModel,
      opSelection: FieldSelection
  ): Either[InternalException, OpArgs[PEvent]] = event match {
    case pragma.domain.Read => {
      val id =
        opSelection.arguments
          .find(_.name == opTargetModel.primaryField.id)
          .map(_.value)
      id match {
        case Some(value) => ReadArgs(sangriaToJson(value)).asRight
        case _ =>
          InternalException(
            "Arguments of Read operation should contain the ID of the record to read"
          ).asLeft
      }
    }
    case ReadMany =>
      opSelection.arguments
        .find(_.name == "aggregation")
        .map(arg => sangriaToJson(arg.value)) match {
        case Some(arg: JsObject) =>
          aggParser.parseModelAgg(opTargetModel, arg).map(ReadManyArgs(_))
        case Some(_) =>
          InternalException(
            s"`agg` argument of LIST operation on `${opTargetModel.id}` must be an object"
          ).asLeft
        case None =>
          ReadManyArgs(ModelAgg(opTargetModel, Nil, None, None)).asRight
      }
    case Create => {
      val objToInsert =
        objFieldsFrom(opSelection.arguments)
          .find(_._1 == opTargetModel.id.small)
          .map(_._2.asJsObject)

      objToInsert match {
        case Some(obj) => CreateArgs(obj).asRight
        case _ =>
          InternalException(
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
          InternalException(
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
    case RemoveManyFrom(arrayField) => {
      val id = opSelection.arguments.collectFirst {
        case arg if arg.name == opTargetModel.primaryField.id =>
          sangriaToJson(arg.value)
      }
      val items = opSelection.arguments.collectFirst {
        case arg if arg.name == "items" => sangriaToJson(arg.value)
      }
      (id, items) match {
        case (Some(id), Some(JsArray(items))) =>
          RemoveManyFromArgs(id, items).asRight
        case (None, Some(_)) =>
          InternalException(
            s"REMOVE_MANY_FROM operation on field `${arrayField.id}` of model `${opTargetModel.id}` takes a `${opTargetModel.primaryField.id}` argument"
          ).asLeft
        case (Some(_), None) =>
          InternalException(
            s"REMOVE_MANY_FROM operation on field `${arrayField.id}` of model `${opTargetModel.id}` takes an `items` array argument"
          ).asLeft
        case _ =>
          InternalException(
            s"REMOVE_MANY_FROM operation on field `${arrayField.id}` of model `${opTargetModel.id}` takes `${opTargetModel.primaryField.id}` and `items` arguments"
          ).asLeft
      }
    }
    case pragma.domain.Update => {
      val objId = opSelection.arguments.collectFirst {
        case arg if arg.name == opTargetModel.primaryField.id =>
          sangriaToJson(arg.value)
      }
      val data = opSelection.arguments.collectFirst {
        case arg if arg.name == opTargetModel.id.small =>
          sangriaToJson(arg.value)
      }
      (objId, data) match {
        case (Some(id), Some(data: JsObject)) =>
          UpdateArgs(ObjectWithId(data, id)).asRight
        case (None, _) =>
          InternalException(
            s"UPDATE arguments must contain the ID of the object to be updated"
          ).asLeft
        case (_, None) =>
          InternalException(
            s"UPDATE arguments of model `${opTargetModel.id}` must contain a `${opTargetModel.id.small}` object"
          ).asLeft
        case _ =>
          InternalException(
            s"UPDATE arguments of model `${opTargetModel.id}` must contain `${opTargetModel.primaryField.id}` and `${opTargetModel.id.small}` arguments"
          ).asLeft
      }
    }
    case UpdateMany => {
      val items = opSelection.arguments.collectFirst {
        case arg if arg.name == "items" => sangriaToJson(arg.value)
      } match {
        case Some(items) => items.asRight
        case None =>
          InternalException("UPDATE_MANY operation takes an `items` argument").asLeft
      }
      val objectsWithIds = items.flatMap {
        case JsArray(items) =>
          items.traverse {
            case JsObject(fields)
                if !fields.contains(opTargetModel.primaryField.id) =>
              InternalException(
                s"Objects in `items` array argument of UPDATE_MANY operation on model `${opTargetModel.id}` must contain a `${opTargetModel.primaryField.id}`"
              ).asLeft
            case _: JsNumber | JsNull | _: JsBoolean | _: JsArray =>
              InternalException(
                s"Values in `items` array argument of UPDATE_MANY operation must be objects containing `${opTargetModel.primaryField.id}`"
              ).asLeft
            case validObject @ JsObject(fields) =>
              ObjectWithId(validObject, fields(opTargetModel.primaryField.id)).asRight
          }
        case _ =>
          InternalException(
            "`items` argument of UPDATE_MANY operation must be an array"
          ).asLeft
      }
      objectsWithIds.map(UpdateManyArgs(_))
    }
    case Login => {
      val credentials =
        (opSelection.arguments, opTargetModel.secretCredentialField) match {
          case (Vector(publicCredentialArg), None) =>
            (
              opTargetModel.fieldsById(publicCredentialArg.name),
              sangriaToJson(publicCredentialArg.value),
              None
            ).asRight
          case (Vector(arg1, arg2), Some(secretCredentialField))
              if arg1.name == secretCredentialField.id =>
            (
              opTargetModel.fieldsById(arg2.name),
              sangriaToJson(arg2.value),
              Some(sangriaToJson(arg1.value))
            ).asRight
          case (Vector(arg1, arg2), Some(secretCredentialField))
              if arg2.name == secretCredentialField.id =>
            (
              opTargetModel.fieldsById(arg1.name),
              sangriaToJson(arg1.value),
              Some(sangriaToJson(arg2.value))
            ).asRight
          case _ =>
            InternalException("Invalid argument list in operation `LOGIN`").asLeft
        }

      credentials.flatMap {
        case (pcField, pcValue, Some(JsString(scValue))) =>
          LoginArgs(
            publicCredentialField = pcField,
            publicCredentialValue = pcValue,
            secretCredentialValue = Some(scValue)
          ).asRight
        case (pcField, pcValue, None) =>
          LoginArgs(
            publicCredentialField = pcField,
            publicCredentialValue = pcValue,
            secretCredentialValue = None
          ).asRight
        case _ =>
          InternalException(
            s"Secret credential value in login operation on model `${opTargetModel.id}` must be a `String`"
          ).asLeft
      }
    }
  }

  private def parseInnerOpArgs(
      opTargetModel: PModel,
      fieldSelection: FieldSelection
  ): Either[InternalException, InnerOpArgs[ReadEvent]] = {
    val modelField = opTargetModel.fieldsById.get(fieldSelection.name)
    modelField match {
      case None =>
        InternalException(
          s"`${fieldSelection.name}` is not field of model `${opTargetModel.id}`"
        ).asLeft
      case Some(field @ PModelField(_, PArray(_), _, _, _, _)) => {
        val agg =
          fieldSelection.arguments.find(_.name == "aggregation").map { aggArg =>
            sangriaToJson(aggArg.value) match {
              case o: JsObject =>
                aggParser.parseArrayFieldAgg(opTargetModel, field, o)
              case _ =>
                InternalException(
                  s"`agg` argument of inner LIST operation on `${field.id}` must be an object"
                ).asLeft
            }
          }
        agg.sequence.map(InnerListArgs(_))
      }
      case _ => InnerOpNoArgs.asRight
    }
  }

}
