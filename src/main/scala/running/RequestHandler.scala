package running

import running.authorizer.Authorizer
import domain._, domain.utils.UserError
import storage.Storage
import cats._
import cats.implicits._
import scala.util._
import spray.json._
import domain.utils.InternalException

class RequestHandler[S, M[_]: Monad](
    syntaxTree: SyntaxTree,
    storage: Storage[S, M]
)(implicit mError: MonadError[M, Throwable]) {
  val reqValidator = new RequestValidator(syntaxTree)
  val authorizer = new Authorizer[S, M](syntaxTree, storage)

  def handle(req: Request): M[Either[Throwable, JsObject]] = {
    val authResult = for {
      validationResult <- reqValidator(req).toEither
      reducedRequest = RequestReducer(validationResult)
      ops <- Operations.from(reducedRequest)(syntaxTree)
      result = authorizer(ops, req.user)
    } yield
      result flatMap [Operations.OperationsMap] { errors =>
        if (!errors.isEmpty)
          MonadError[M, Throwable].raiseError(UserError.fromAuthErrors(errors))
        else ops.pure[M]
      }

    val opsAfterPreHooks = authResult.map { result =>
      result.flatMap[Operations.OperationsMap] { opsMap =>
        val newOps = opsMap.toVector
          .traverse {
            case (groupName, opModelGroups) =>
              opModelGroups.toVector
                .traverse {
                  case (modelSelectionName, ops) =>
                    ops.traverse(applyPreHooks).map(modelSelectionName -> _)
                }
                .map(groupName -> _.toMap)
          }
          .map(_.toMap)

        newOps match {
          case Left(t)    => mError.raiseError(t)
          case Right(ops) => Monad[M].pure(ops)
        }
      }
    }

    val storageResult =
      opsAfterPreHooks.traverse { ops =>
        req.body.flatMap(_.fields.get("operationName")) match {
          case Some(JsString(opName)) =>
            ops.flatMap { ops =>
              val op = ops.filter {
                case (Some(op), _) => op == opName
                case _             => false
              }
              storage.run(op)
            }
          case _ => ops.flatMap(storage.run)
        }
      }

    val readHookResults = storageResult.map { result =>
      result
        .flatMap { transactionMap =>
          transactionMap.traverse {
            case (groupName, modelGroups) =>
              modelGroups
                .traverse {
                  case (modelGroupName, ops) =>
                    ops
                      .traverse {
                        case (op, res) => applyReadHooks(op, res).map(op -> _)
                      }
                      .map(modelGroupName -> _)
                }
                .map(groupName -> _)
          }
        }
    }

    readHookResults.map { result =>
      result.map(data => JsObject(Map("data" -> transactionResultJson(data))))
    }

  }

  private def transactionResultJson(
      resultMap: storage.queryEngine.TransactionResultMap
  ): JsObject = {
    val opGroupResults = for {
      (groupName, modelGroups) <- resultMap
      groupResultFields = modelGroups.map {
        case (modelGroupName, ops) =>
          modelGroupName -> JsObject {
            ops.map {
              case (op, result) => op.name -> withInnerOpsAliases(op, result)
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
      hooks: Seq[PFunctionValue[JsValue, Try[JsValue]]],
      args: JsValue
  ): Either[Throwable, JsValue] =
    hooks.foldLeft(args.asRight[Throwable]) {
      case (acc, hook) => acc.flatMap(hook(_).toEither)
    }

  /** Apples crud hooks to operation arguments */
  private def applyPreHooks(op: Operation): Either[Throwable, Operation] =
    op match {
      case createOp: CreateOperation =>
        applyHooks(createOp.hooks, createOp.opArguments.obj)
          .flatMap {
            case obj: JsObject => obj.asRight
            case _ =>
              UserError(
                s"Result of write hooks applied to ${createOp.event} operation arguments must be an object"
              ).asLeft
          }
          .map { res =>
            createOp.copy(opArguments = createOp.opArguments.copy(obj = res))
          }
      case createManyOp: CreateManyOperation =>
        createManyOp.opArguments.items.toVector
          .traverse { item =>
            applyHooks(createManyOp.hooks, item).flatMap {
              case obj: JsObject => obj.asRight
              case _ =>
                UserError(
                  s"Result of write hooks applied to ${createManyOp.event} must be an object"
                ).asLeft
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
              .asRight
          case _ =>
            UserError(
              s"Result of write hooks applied to ${updateOp.event} argument must be an object"
            ).asLeft
        }
      case updateManyOp: UpdateManyOperation =>
        updateManyOp.opArguments.items.toVector
          .traverse { obj =>
            applyHooks(updateManyOp.hooks, obj.obj).flatMap {
              case newObj: JsObject => obj.copy(obj = newObj).asRight
              case _ =>
                UserError(
                  s"Result of write hooks applied to ${updateManyOp.event} argument must be an object"
                ).asLeft
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
        applyHooks(loginOp.hooks, JsObject(credObjFields.toMap)) flatMap {
          case JsObject(fields)
              if loginOp.targetModel.secretCredentialField.isDefined &&
                fields.contains(secretCredPair.get._1) &&
                fields.contains(loginOp.opArguments.publicCredentialField.id) =>
            fields
              .get(loginOp.targetModel.secretCredentialField.get.id)
              .flatMap {
                case JsString(value) => Some(value)
                case _               => None
              }
              .map { secretValue =>
                loginOp
                  .copy(
                    opArguments = LoginArgs(
                      loginOp.opArguments.publicCredentialField,
                      fields(loginOp.opArguments.publicCredentialField.id),
                      Some(secretValue)
                    )
                  )
                  .asRight
              }
              .getOrElse {
                UserError(
                  s"Invalid secret credential field in `LOGIN` hook result on model `${loginOp.targetModel.id}`"
                ).asLeft
              }
          case JsObject(fields)
              if fields.contains(
                loginOp.opArguments.publicCredentialField.id
              ) =>
            loginOp
              .copy(
                opArguments = LoginArgs(
                  loginOp.opArguments.publicCredentialField,
                  fields(loginOp.opArguments.publicCredentialField.id),
                  None
                )
              )
              .asRight
          case _ =>
            UserError(
              s"Invalid credentials returned from `LOGIN` hook on model `${loginOp.targetModel.id}`"
            ).asLeft
        }
      }
      case _ => op.asRight
    }

  /** Applies read hooks to operation results */
  private def applyReadHooks(
      op: Operation,
      opResult: JsValue
  ): Either[Throwable, JsValue] =
    opResult match {
      case JsObject(fields) =>
        op.innerReadOps
          .traverse { iop =>
            val fieldValue = fields.get(iop.targetField.field.id)
            fieldValue
              .map(applyInnerOpHooks(iop, _).map(iop.targetField.field.id -> _))
              .getOrElse((iop.targetField.field.id -> JsNull).asRight)
          }
          .flatMap { newFields =>
            applyHooks(op.targetModel.readHooks, JsObject(newFields.toMap))
          }
      case JsArray(elements) =>
        elements.traverse(applyReadHooks(op, _)).map(JsArray(_))
      case s: JsString => s.asRight
      case _ =>
        InternalException(
          s"Result of ${op.event} operation must either be an array or an object"
        ).asLeft
    }

  protected def applyInnerOpHooks(
      iop: InnerOperation,
      iopResult: JsValue
  ): Either[Throwable, JsValue] = iop match {
    case _: InnerReadOperation if iopResult == JsNull => JsNull.asRight
    case innerReadOp: InnerReadOperation => {
      val iopFieldIsRef = innerReadOp.targetField.field.ptype match {
        case PReference(refId) if syntaxTree.modelsById.contains(refId) => true
        case POption(PReference(refId))
            if syntaxTree.modelsById.contains(refId) =>
          true
        case _ => false
      }
      if (iopFieldIsRef) for {
        oldFields <- iopResult match {
          case JsObject(fields) => fields.asRight
          case nonObj =>
            InternalException(
              s"Result of inner read operation must be an object, but $nonObj was passed to inner operation hook application function"
            ).asLeft
        }
        newFields <- innerReadOp.innerReadOps
          .traverse { iiop =>
            oldFields
              .get(iiop.targetField.field.id)
              .map { fieldValue =>
                applyInnerOpHooks(iiop, fieldValue)
                  .map(iiop.targetField.field.id -> _)
              }
              .getOrElse((iiop.targetField.field.id -> JsNull).asRight)
          }
        newObj <- applyHooks(innerReadOp.hooks, JsObject(newFields.toMap))
      } yield newObj
      else iopResult.asRight
    }
    case innerReadManyOp: InnerReadManyOperation => {
      val fieldIsRefArray = innerReadManyOp.targetField.field.ptype match {
        case PArray(PReference(refId))
            if syntaxTree.modelsById.contains(refId) =>
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
        case nonRefArray: JsArray => nonRefArray.asRight
        case _ =>
          InternalException("Result of list operation must be an array").asLeft
      }
    }
  }

}
