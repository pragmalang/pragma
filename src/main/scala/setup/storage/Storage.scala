package setup.storage

import setup._

import domain.SyntaxTree
import spray.json._
import scala.util._
import running.pipeline.Operation
import domain._
import sangria.ast._
import sangria.ast.{Document => GqlDocument}
import domain.Implicits._
import running.pipeline.InnerOperation
import running.Implicits._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits._
import running.pipeline._

trait Storage {
  def run(
      query: GqlDocument,
      operations: Map[Option[String], Vector[Operation]]
  ): Future[Try[Either[JsObject, Vector[JsObject]]]]

  def migrate(migrationSteps: Vector[MigrationStep]): Future[Vector[Try[Unit]]]

  def modelExists(model: PModel): Future[Boolean]
  def modelEmpty(model: PModel): Future[Boolean]
}

trait NoSqlStorage extends Storage {

  import Storage._
  override def run(
      query: GqlDocument,
      operations: Map[Option[String], Vector[Operation]]
  ): Future[Try[Either[JsObject, Vector[JsObject]]]] = {
    val res: Map[Option[String], Future[Map[String, JsValue]]] =
      operations
        .map {
          case (opName, ops) =>
            opName -> Future
              .sequence(ops.map { op =>
                (op.event match {
                  case Read =>
                    readOneRecord(
                      op.targetModel,
                      op.opArguments
                        .find(
                          arg =>
                            arg.name == op.targetModel.primaryField.id.small
                        )
                        .get
                        .value match {
                        case value: IntValue    => Left(BigInt(value.value))
                        case value: BigIntValue => Left(value.value)
                        case value: StringValue => Right(value.value)
                        case value =>
                          throw new InternalError(
                            s"`Storage#readOneRecord` got ${value.getClass().getName()} and it only takes `Either[BigInt, String]`"
                          )
                      },
                      op.innerReadOps
                    ).map(
                      res =>
                        op.alias
                          .getOrElse(op.event.render(op.targetModel)) -> select(
                          res,
                          op.innerReadOps
                        )
                    )
                  case ReadMany =>
                    readManyRecords(
                      op.targetModel,
                      op.opArguments
                        .find(_.name == "where")
                        .get
                        .value
                        .toJson
                        .asJsObject
                        .convertTo[QueryWhere],
                      op.innerReadOps
                    ).map(
                      res =>
                        op.alias
                          .getOrElse(op.event.render(op.targetModel)) -> JsArray(
                          res.elements.map {
                            case obj: JsObject => select(obj, op.innerReadOps)
                            case v             => v
                          }
                        )
                    )
                  case Create =>
                    createOneRecord(
                      op.targetModel,
                      op.opArguments
                        .find(_.name == op.targetModel.id.small)
                        .get
                        .value
                        .toJson
                        .asJsObject,
                      op.innerReadOps
                    ).map(
                      res =>
                        op.alias
                          .getOrElse(op.event.render(op.targetModel)) -> select(
                          res,
                          op.innerReadOps
                        )
                    )
                  case CreateMany =>
                    createManyRecords(
                      op.targetModel,
                      op.opArguments
                        .find(_.name == "items")
                        .get
                        .value
                        .toJson
                        .asInstanceOf[JsArray]
                        .elements
                        .map(_.asJsObject),
                      op.innerReadOps
                    ).map(
                      res =>
                        op.alias
                          .getOrElse(op.event.render(op.targetModel)) -> JsArray(
                          res.elements.map {
                            case obj: JsObject => select(obj, op.innerReadOps)
                            case v             => v
                          }
                        )
                    )
                  case Update =>
                    updateOneRecord(
                      op.targetModel,
                      op.opArguments
                        .find(
                          arg =>
                            arg.name == op.targetModel.primaryField.id.small
                        )
                        .get
                        .value match {
                        case value: IntValue    => Left(BigInt(value.value))
                        case value: BigIntValue => Left(value.value)
                        case value: StringValue => Right(value.value)
                        case value =>
                          throw new InternalError(
                            s"`Storage#readOneRecord` got ${value.getClass().getName()} and it only takes `Either[BigInt, String]`"
                          )
                      },
                      op.opArguments
                        .find(_.name == op.targetModel.id.small)
                        .get
                        .value
                        .toJson
                        .asJsObject,
                      op.innerReadOps
                    ).map(
                      res =>
                        op.alias
                          .getOrElse(op.event.render(op.targetModel)) -> select(
                          res,
                          op.innerReadOps
                        )
                    )
                  case UpdateMany =>
                    updateManyRecords(
                      op.targetModel,
                      op.opArguments
                        .find(_.name == "items")
                        .get
                        .value
                        .toJson
                        .asInstanceOf[JsArray]
                        .elements
                        .map(_.asJsObject),
                      op.innerReadOps
                    ).map(
                      res =>
                        op.alias
                          .getOrElse(op.event.render(op.targetModel)) -> JsArray(
                          res.elements.map {
                            case obj: JsObject => select(obj, op.innerReadOps)
                            case v             => v
                          }
                        )
                    )
                  case Delete =>
                    deleteOneRecord(
                      op.targetModel,
                      op.opArguments
                        .find(
                          arg =>
                            arg.name == op.targetModel.primaryField.id.small
                        )
                        .get
                        .value match {
                        case value: IntValue    => Left(BigInt(value.value))
                        case value: BigIntValue => Left(value.value)
                        case value: StringValue => Right(value.value)
                        case value =>
                          throw new InternalError(
                            s"`Storage#readOneRecord` got ${value.getClass().getName()} and it only takes `Either[BigInt, String]`"
                          )
                      },
                      op.innerReadOps
                    ).map(
                      res =>
                        op.alias
                          .getOrElse(op.event.render(op.targetModel)) -> select(
                          res,
                          op.innerReadOps
                        )
                    )
                  case DeleteMany => {
                    val itemsArg =
                      op.opArguments.find(_.name == "items")
                    val filter = itemsArg match {
                      case Some(arg) => {
                        Right(
                          arg.value.toJson
                            .asInstanceOf[JsArray]
                            .elements
                            .collect {
                              case item: JsString => Left(item.value)
                              case item: JsNumber =>
                                Right(item.value.toBigInt)
                            }
                        )
                      }
                      case None =>
                        Left(
                          op.directives
                            .find(_.name == "filter")
                            .get
                            .arguments
                            .find(_.name == "filter")
                            .get
                            .value
                            .toJson
                            .convertTo[QueryFilter]
                        )
                    }
                    deleteManyRecords(op.targetModel, filter, op.innerReadOps)
                      .map(
                        res =>
                          op.alias
                            .getOrElse(op.event.render(op.targetModel)) -> JsArray(
                            res.elements.map {
                              case obj: JsObject => select(obj, op.innerReadOps)
                              case v             => v
                            }
                          )
                      )
                  }
                  case PushTo(listField) =>
                    pushOneTo(
                      op.targetModel,
                      listField,
                      op.opArguments.find(_.name == "item").get.value.toJson,
                      op.opArguments
                        .find(_.name == op.targetModel.primaryField.id)
                        .get
                        .value
                        .toJson match {
                        case JsString(value) => Right(value)
                        case JsNumber(value) => Left(value.toBigInt)
                        case _ =>
                          throw new InternalError(
                            "primary key values can only be of type `Int` or `String`"
                          )
                      },
                      op.innerReadOps
                    ).map(
                      res =>
                        op.alias
                          .getOrElse(op.event.render(op.targetModel)) -> (res match {
                          case obj: JsObject => select(obj, op.innerReadOps)
                          case v             => v
                        })
                    )
                  case PushManyTo(listField) =>
                    pushManyTo(
                      op.targetModel,
                      listField,
                      op.opArguments
                        .find(_.name == "items")
                        .get
                        .value
                        .toJson
                        .asInstanceOf[JsArray]
                        .elements,
                      op.opArguments
                        .find(_.name == op.targetModel.primaryField.id)
                        .get
                        .value
                        .toJson match {
                        case JsString(value) => Right(value)
                        case JsNumber(value) => Left(value.toBigInt)
                        case _ =>
                          throw new InternalError(
                            "primary key values can only be of type `Int` or `String`"
                          )
                      },
                      op.innerReadOps
                    ).map(
                      res =>
                        op.alias
                          .getOrElse(op.event.render(op.targetModel)) -> JsArray(
                          res.elements.map {
                            case obj: JsObject => select(obj, op.innerReadOps)
                            case v             => v
                          }
                        )
                    )
                  case RemoveFrom(listField) =>
                    removeOneFrom(
                      op.targetModel,
                      listField,
                      op.opArguments.find(_.name == "item").get.value.toJson,
                      op.opArguments
                        .find(_.name == op.targetModel.primaryField.id)
                        .get
                        .value
                        .toJson match {
                        case JsString(value) => Right(value)
                        case JsNumber(value) => Left(value.toBigInt)
                        case _ =>
                          throw new InternalError(
                            "primary key values can only be of type `Int` or `String`"
                          )
                      },
                      op.innerReadOps
                    ).map(
                      res =>
                        op.alias
                          .getOrElse(op.event.render(op.targetModel)) -> (res match {
                          case obj: JsObject => select(obj, op.innerReadOps)
                          case v             => v
                        })
                    )
                  case RemoveManyFrom(listField) =>
                    removeManyFrom(
                      op.targetModel,
                      listField,
                      op.opArguments
                        .find(_.name == "filter")
                        .get
                        .value
                        .toJson
                        .convertTo[QueryFilter],
                      op.opArguments
                        .find(_.name == op.targetModel.primaryField.id)
                        .get
                        .value
                        .toJson match {
                        case JsString(value) => Right(value)
                        case JsNumber(value) => Left(value.toBigInt)
                        case _ =>
                          throw new InternalError(
                            "primary key values can only be of type `Int` or `String`"
                          )
                      },
                      op.innerReadOps
                    ).map(
                      res =>
                        op.alias
                          .getOrElse(op.event.render(op.targetModel)) -> JsArray(
                          res.elements.map {
                            case obj: JsObject => select(obj, op.innerReadOps)
                            case v             => v
                          }
                        )
                    )
                  case Login =>
                    throw new InternalError(
                      "`LOGIN` event can't be handled by `Storage`"
                    )
                })
              })
              .map(_.toMap)
        }

    Future
      .sequence(res.map {
        case (gqlOpName, future) => future.map(gqlOpName -> _)
      })
      .map(_.toVector.map(_._2))
      .map(
        v =>
          Success {
            if (v.length == 1) {
              Left(JsObject(v.head))
            } else {
              Right(v.map(JsObject(_)))
            }
          }
      )
      .recover(Failure(_))
  }

  def createManyRecords(
      model: PModel,
      records: Vector[JsObject],
      innerReadOps: Vector[InnerOperation]
  ): Future[JsArray]

  def createOneRecord(
      model: PModel,
      record: JsObject,
      innerReadOps: Vector[InnerOperation]
  ): Future[JsObject]

  def updateManyRecords(
      model: PModel,
      recordsWithIds: Vector[JsObject],
      innerReadOps: Vector[InnerOperation]
  ): Future[JsArray]

  def updateOneRecord(
      model: PModel,
      primaryKeyValue: Either[BigInt, String],
      newRecord: JsObject,
      innerReadOps: Vector[InnerOperation]
  ): Future[JsObject]

  def deleteManyRecords(
      model: PModel,
      filter: Either[QueryFilter, Vector[Either[String, BigInt]]],
      innerReadOps: Vector[InnerOperation]
  ): Future[JsArray]

  def deleteOneRecord(
      model: PModel,
      primaryKeyValue: Either[BigInt, String],
      innerReadOps: Vector[InnerOperation]
  ): Future[JsObject]

  def pushManyTo(
      model: PModel,
      field: PShapeField,
      items: Vector[JsValue],
      primaryKeyValue: Either[BigInt, String],
      innerReadOps: Vector[InnerOperation]
  ): Future[JsArray]

  def pushOneTo(
      model: PModel,
      field: PShapeField,
      item: JsValue,
      primaryKeyValue: Either[BigInt, String],
      innerReadOps: Vector[InnerOperation]
  ): Future[JsValue]

  def removeManyFrom(
      model: PModel,
      field: PShapeField,
      filter: QueryFilter,
      primaryKeyValue: Either[BigInt, String],
      innerReadOps: Vector[InnerOperation]
  ): Future[JsArray]

  def removeOneFrom(
      model: PModel,
      field: PShapeField,
      item: JsValue,
      primaryKeyValue: Either[BigInt, String],
      innerReadOps: Vector[InnerOperation]
  ): Future[JsValue]

  def readManyRecords(
      model: PModel,
      where: QueryWhere,
      innerReadOps: Vector[InnerOperation]
  ): Future[JsArray]

  def readOneRecord(
      model: PModel,
      primaryKeyValue: Either[BigInt, String],
      innerReadOps: Vector[InnerOperation]
  ): Future[JsObject]
}

case class MockStorage(syntaxTree: SyntaxTree) extends Storage {

  override def modelEmpty(model: PModel): Future[Boolean] = Future(true)
  override def modelExists(model: PModel): Future[Boolean] = Future(true)

  override def run(
      query: GqlDocument,
      operations: Map[Option[String], Vector[Operation]]
  ): Future[Try[Either[JsObject, Vector[JsObject]]]] =
    Future(
      Success(
        Left(
          JsObject(
            Map(
              "username" -> JsString("John Doe"),
              "todos" -> JsArray(
                Vector(
                  JsObject(
                    Map(
                      "content" -> JsString("Wash the dishes"),
                      "done" -> JsTrue
                    )
                  ),
                  JsObject(
                    Map(
                      "content" -> JsString("Pick up the kids"),
                      "done" -> JsFalse
                    )
                  )
                )
              )
            )
          )
        )
      )
    )

  override def migrate(
      migrationSteps: Vector[MigrationStep]
  ): Future[Vector[Try[Unit]]] =
    Future(Vector.empty)
}

object Storage {
  def select(obj: JsObject, innerReadOps: Vector[InnerOperation]): JsObject = {
    val selectedFields = obj.fields
      .filter(
        field => innerReadOps.map(_.targetField.field.id).contains(field._1)
      )
    JsObject(
      selectedFields
        .map(
          field => {
            val op = innerReadOps
              .find(_.targetField.field.id == field._1)
              .get
              .operation
            val fieldKey = op.alias.getOrElse(field._1)

            field._2 match {
              case obj: JsObject =>
                fieldKey -> select(
                  obj,
                  op.innerReadOps
                )
              case JsArray(elements) if elements.forall {
                    case _: JsObject => true
                    case _           => false
                  } =>
                fieldKey -> JsArray(
                  elements.map(e => select(e.asJsObject, op.innerReadOps))
                )
              case value => (fieldKey -> value)
            }
          }
        )
    )
  }
}
