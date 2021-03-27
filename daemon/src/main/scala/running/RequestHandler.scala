package running

import running.authorizer.Authorizer, running.operations._
import pragma.domain._, utils.{UserError, InternalException}
import storage.Storage
import cats._
import cats.implicits._
import scala.util._
import spray.json._
import cats.effect.Async
import cats.effect.ConcurrentEffect

class RequestHandler[S, M[_]: Async: ConcurrentEffect](
    syntaxTree: SyntaxTree,
    storage: Storage[S, M],
    funcExecutor: PFunctionExecutor[M]
)(implicit MError: MonadError[M, Throwable]) {
  val reqValidator = new RequestValidator(syntaxTree)
  val authorizer = new Authorizer[S, M](syntaxTree, storage, funcExecutor)
  val opParser = new OperationParser(syntaxTree)

  def handle(req: Request): M[JsObject] = {
    val ops: M[Operations.OperationsMap] = {
      val res = (
        for {
          validationResult <- reqValidator(req).toEither
          reducedRequest = RequestReducer(validationResult)
          ops <- opParser.parse(reducedRequest)
        } yield ops
      ) match {
        case Left(err)    => MError.raiseError(err)
        case Right(value) => value.pure[M]
      }
      res.widen[Operations.OperationsMap]
    }

    val authResult = for {
      ops <- ops
      result = authorizer(ops, req.user)
    } yield result flatMap [Operations.OperationsMap] { errors =>
      if (!errors.isEmpty)
        MonadError[M, Throwable].raiseError(UserError.fromAuthErrors(errors))
      else ops.pure[M]
    }

    val opsAfterPreHooks = authResult.flatMap { result =>
      result.flatMap[Operations.OperationsMap] { opsMap =>
        opsMap.toVector
          .traverse { case (groupName, opModelGroups) =>
            opModelGroups.toVector
              .traverse { case (modelSelectionName, ops) =>
                ops.traverse(applyPreHooks).map(modelSelectionName -> _)
              }
              .map(groupName -> _.toMap)
          }
          .map(_.toMap)
      }
    }

    val storageResult = opsAfterPreHooks.flatMap { ops =>
      req.body.flatMap(_.fields.get("operationName")) match {
        case Some(JsString(gqlOpName)) =>
          storage.run(ops.filter(_._1 == Some(gqlOpName)))
        case _ => storage.run(ops)
      }
    }

    val readHookResults = storageResult.flatMap { result =>
      result.traverse { case (groupName, modelGroups) =>
        modelGroups
          .traverse { case (modelGroupName, ops) =>
            ops
              .traverse {
                case (loginOp: LoginOperation, token) =>
                  (loginOp, token).pure[M].widen[(Operation, JsValue)]
                case (op, res) =>
                  applyReadHooks(op, res).widen[JsValue].map(op -> _)
              }
              .map(modelGroupName -> _)
          }
          .map(groupName -> _)
      }
    }

    readHookResults.map { result =>
      JsObject(Map("data" -> transactionResultJson(result)))
    }

  }

  private def transactionResultJson(
      resultMap: storage.queryEngine.TransactionResultMap
  ): JsObject = {
    val opGroupResults = for {
      (groupName, modelGroups) <- resultMap
      groupResultFields = modelGroups.map { case (modelGroupName, ops) =>
        modelGroupName -> JsObject {
          ops.map { case (op, result) =>
            op.name -> withInnerOpsAliases(op, result)
          }.toMap
        }
      }.toMap
      groupResultJson = JsObject(groupResultFields)
    } yield (groupName.getOrElse("data"), groupResultJson)

    if (opGroupResults.length == 1) opGroupResults.head._2
    else JsObject(opGroupResults.toMap)
  }

  /** To be used in functions that convert storage results to JSON */
  private def withInnerOpsAliases(
      op: Operation,
      storageResult: JsValue
  ): JsValue = storageResult match {
    case JsObject(fields) => {
      val newFields = op.innerReadOps.map { iop =>
        val fieldWithAliasedInnards = fields(iop.targetField.field.id) match {
          case obj: JsObject => withInnerOpsAliases(iop, obj)
          case JsArray(elements) =>
            JsArray(elements.map(withInnerOpsAliases(iop, _)))
          case otherValue => otherValue
        }
        iop.name -> fieldWithAliasedInnards
      }
      JsObject(newFields.toMap)
    }
    case JsArray(elements) =>
      JsArray(elements.map(withInnerOpsAliases(op, _)))
    case _ => storageResult
  }

  private def applyHooks(
      hooks: Seq[PFunctionValue],
      arg: JsObject
  ): M[JsValue] =
    hooks.foldLeft(arg.pure[M].widen[JsValue]) { case (acc, hook) =>
      acc.flatMap(a => funcExecutor.execute(hook, a :: Nil))
    }

  /** Apples crud hooks to operation arguments */
  private def applyPreHooks(op: Operation): M[Operation] =
    op match {
      case createOp: CreateOperation =>
        applyHooks(createOp.hooks, createOp.opArguments.obj)
          .flatMap {
            case obj: JsObject => obj.pure[M]
            case _ =>
              MonadError[M, Throwable].raiseError[JsObject] {
                UserError(
                  s"Result of write hooks applied to ${createOp.event} operation arguments must be an object"
                )
              }
          }
          .map { res =>
            createOp.copy(opArguments = createOp.opArguments.copy(obj = res))
          }
      case createManyOp: CreateManyOperation =>
        createManyOp.opArguments.items.toVector
          .traverse { item =>
            applyHooks(createManyOp.hooks, item).flatMap {
              case obj: JsObject => obj.pure[M]
              case _ =>
                MonadError[M, Throwable].raiseError[JsObject] {
                  UserError(
                    s"Result of write hooks applied to ${createManyOp.event} must be an object"
                  )
                }
            }
          }
          .map { newItems =>
            createManyOp.copy(
              opArguments = createManyOp.opArguments.copy(items = newItems)
            )
          }
      case updateOp: UpdateOperation =>
        applyHooks(updateOp.hooks, updateOp.opArguments.obj.obj).flatMap {
          case obj: JsObject =>
            updateOp
              .copy(
                opArguments = updateOp.opArguments
                  .copy(obj = updateOp.opArguments.obj.copy(obj = obj))
              )
              .pure[M]
              .widen[Operation]
          case _ =>
            MonadError[M, Throwable].raiseError[Operation] {
              UserError(
                s"Result of write hooks applied to ${updateOp.event} argument must be an object"
              )
            }
        }
      case updateManyOp: UpdateManyOperation =>
        updateManyOp.opArguments.items.toVector
          .traverse { obj =>
            applyHooks(updateManyOp.hooks, obj.obj).flatMap {
              case newObj: JsObject => obj.copy(obj = newObj).pure[M]
              case _ =>
                MonadError[M, Throwable].raiseError[ObjectWithId] {
                  UserError(
                    s"Result of write hooks applied to ${updateManyOp.event} argument must be an object"
                  )
                }
            }
          }
          .map { newItems =>
            updateManyOp.copy(
              opArguments = updateManyOp.opArguments.copy(items = newItems)
            )
          }
      case deleteOp: DeleteOperation =>
        applyHooks(
          deleteOp.hooks,
          JsObject(
            deleteOp.targetModel.primaryField.id -> deleteOp.opArguments.id
          )
        ) map { result =>
          deleteOp.copy(opArguments = deleteOp.opArguments.copy(id = result))
        }
      case loginOp: LoginOperation => {
        val publicCredPair =
          loginOp.opArguments.publicCredentialField.id -> loginOp.opArguments.publicCredentialValue
        val secretCredPair =
          loginOp.targetModel.secretCredentialField.map(_.id) zip
            loginOp.opArguments.secretCredentialValue.map(JsString(_))
        val credObjFields = publicCredPair :: secretCredPair
          .map(_ :: Nil)
          .getOrElse(Nil)
        applyHooks(loginOp.hooks, JsObject(credObjFields.toMap)).as(loginOp)
      }
      case _ => op.pure[M]
    }

  /** Applies read hooks to operation results */
  private def applyReadHooks(
      op: Operation,
      opResult: JsValue
  ): M[JsValue] =
    opResult match {
      case JsObject(fields) =>
        op.innerReadOps
          .traverse { iop =>
            val fieldValue = fields.get(iop.targetField.field.id)
            fieldValue
              .map(applyInnerOpHooks(iop, _).map(iop.targetField.field.id -> _))
              .getOrElse(
                (iop.targetField.field.id -> JsNull).widen[JsValue].pure[M]
              )
          }
          .flatMap { newFields =>
            applyHooks(op.targetModel.readHooks, JsObject(newFields.toMap))
              .widen[JsValue]
          }
      case JsArray(elements) =>
        elements
          .traverse(applyReadHooks(op, _))
          .map(JsArray(_))
      case _ =>
        MonadError[M, Throwable].raiseError[JsValue] {
          InternalException(
            s"Result of ${op.event} operation must either be an array or an object"
          )
        }
    }

  protected def applyInnerOpHooks(
      iop: InnerOperation,
      iopResult: JsValue
  ): M[JsValue] = iop match {
    case _: InnerReadOperation if iopResult == JsNull =>
      JsNull.pure[M].widen[JsValue]
    case innerReadOp: InnerReadOperation => {
      val iopFieldIsRef = innerReadOp.targetField.field.ptype match {
        case PReference(refId) if syntaxTree.modelsById.contains(refId) => true
        case POption(PReference(refId)) if syntaxTree.modelsById.contains(refId) =>
          true
        case _ => false
      }
      if (iopFieldIsRef) for {
        oldFields <- iopResult match {
          case JsObject(fields) => fields.pure[M]
          case nonObj =>
            MonadError[M, Throwable].raiseError[Map[String, JsValue]] {
              InternalException(
                s"Result of inner read operation must be an object, but $nonObj was passed to inner operation hook application function"
              )
            }
        }
        newFields <- innerReadOp.innerReadOps
          .traverse { iiop =>
            oldFields
              .get(iiop.targetField.field.id)
              .map { fieldValue =>
                applyInnerOpHooks(iiop, fieldValue)
                  .map(iiop.targetField.field.id -> _)
              }
              .getOrElse {
                (iiop.targetField.field.id -> JsNull).widen[JsValue].pure[M]
              }
          }
        newObj <- applyHooks(innerReadOp.hooks, JsObject(newFields.toMap))
      } yield newObj
      else iopResult.pure[M]
    }
    case innerReadManyOp: InnerReadManyOperation => {
      val fieldIsRefArray = innerReadManyOp.targetField.field.ptype match {
        case PArray(PReference(refId)) if syntaxTree.modelsById.contains(refId) =>
          true
        case _ => false
      }
      iopResult match {
        case JsArray(elements) if fieldIsRefArray => {
          val innerReadOneOp = InnerReadOperation(
            innerReadManyOp.targetField,
            innerReadManyOp.targetModel,
            innerReadManyOp.user,
            innerReadManyOp.hooks,
            innerReadManyOp.innerReadOps
          )
          elements
            .traverse(applyInnerOpHooks(innerReadOneOp, _))
            .map(JsArray(_))
        }
        case nonRefArray: JsArray => nonRefArray.pure[M].widen[JsValue]
        case _ =>
          MonadError[M, Throwable].raiseError[JsValue] {
            InternalException("Result of list operation must be an array")
          }
      }
    }
  }

}
