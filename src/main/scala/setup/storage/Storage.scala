package setup.storage

import setup._

import spray.json._
import scala.util._
import running.pipeline.Operation
import domain._
import running.pipeline.InnerOperation
import running.pipeline._
import cats.Monad

class Storage[S, M[_]: Monad](
    queryEngine: QueryEngine[S, M],
    migrationEngine: MigrationEngine[S, M]
) {

  def run(
      operations: Map[Option[String], Vector[Operation]]
  ): M[Either[JsObject, Vector[JsObject]]] =
    queryEngine.run(operations)

  def migrate(
      migrationSteps: Vector[MigrationStep]
  ): M[Vector[Try[Unit]]] = migrationEngine.migrate(migrationSteps)
}

trait MigrationEngine[S, M[_]] {
  def migrate(
      migrationSteps: Vector[MigrationStep]
  ): M[Vector[Try[Unit]]]
}

trait QueryEngine[S, M[_]] {

  def run(
      operations: Map[Option[String], Vector[Operation]]
  ): M[Either[JsObject, Vector[JsObject]]]

  def createManyRecords(
      model: PModel,
      records: Vector[JsObject],
      innerReadOps: Vector[InnerOperation]
  ): M[JsArray]

  def createOneRecord(
      model: PModel,
      record: JsObject,
      innerReadOps: Vector[InnerOperation]
  ): M[JsObject]

  def updateManyRecords(
      model: PModel,
      recordsWithIds: Vector[JsObject],
      innerReadOps: Vector[InnerOperation]
  ): M[JsArray]

  def updateOneRecord(
      model: PModel,
      primaryKeyValue: Either[BigInt, String],
      newRecord: JsObject,
      innerReadOps: Vector[InnerOperation]
  ): M[JsObject]

  def deleteManyRecords(
      model: PModel,
      filter: Either[QueryFilter, Vector[Either[String, BigInt]]],
      innerReadOps: Vector[InnerOperation]
  ): M[JsArray]

  def deleteOneRecord(
      model: PModel,
      primaryKeyValue: Either[BigInt, String],
      innerReadOps: Vector[InnerOperation]
  ): M[JsObject]

  def pushManyTo(
      model: PModel,
      field: PShapeField,
      items: Vector[JsValue],
      primaryKeyValue: Either[BigInt, String],
      innerReadOps: Vector[InnerOperation]
  ): M[JsArray]

  def pushOneTo(
      model: PModel,
      field: PShapeField,
      item: JsValue,
      primaryKeyValue: Either[BigInt, String],
      innerReadOps: Vector[InnerOperation]
  ): M[JsValue]

  def removeManyFrom(
      model: PModel,
      field: PShapeField,
      filter: QueryFilter,
      primaryKeyValue: Either[BigInt, String],
      innerReadOps: Vector[InnerOperation]
  ): M[JsArray]

  def removeOneFrom(
      model: PModel,
      field: PShapeField,
      item: JsValue,
      primaryKeyValue: Either[BigInt, String],
      innerReadOps: Vector[InnerOperation]
  ): M[JsValue]

  def readManyRecords(
      model: PModel,
      where: QueryWhere,
      innerReadOps: Vector[InnerOperation]
  ): M[JsArray]

  def readOneRecord(
      model: PModel,
      primaryKeyValue: Either[BigInt, String],
      innerReadOps: Vector[InnerOperation]
  ): M[JsObject]
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
