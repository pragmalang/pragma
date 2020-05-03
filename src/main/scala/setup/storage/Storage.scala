package setup.storage

import setup._

import spray.json._
import scala.util._
import running.pipeline.Operation
import domain._
import sangria.ast.{Document => GqlDocument}
import running.pipeline.InnerOperation
import running.pipeline._
import concurrent.Future

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
