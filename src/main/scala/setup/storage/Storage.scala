package setup.storage

import setup._

import spray.json._
import scala.util._
import running.pipeline.Operation
import domain._
import running.pipeline.InnerOperation
import cats.Monad
import doobie._

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
  type Query[_]

  def run(
      operations: Map[Option[String], Vector[Operation]]
  ): M[Either[JsObject, Vector[JsObject]]]

  def createManyRecords(
      model: PModel,
      records: Vector[JsObject],
      innerReadOps: Vector[InnerOperation]
  ): Query[JsArray]

  def createOneRecord(
      model: PModel,
      record: JsObject,
      innerReadOps: Vector[InnerOperation]
  ): Query[JsObject]

  def updateManyRecords(
      model: PModel,
      recordsWithIds: Vector[JsObject],
      innerReadOps: Vector[InnerOperation]
  ): Query[JsArray]

  def updateOneRecord(
      model: PModel,
      primaryKeyValue: Either[BigInt, String],
      newRecord: JsObject,
      innerReadOps: Vector[InnerOperation]
  ): Query[JsObject]

  def deleteManyRecords(
      model: PModel,
      filter: Either[QueryFilter, Vector[Either[String, BigInt]]],
      innerReadOps: Vector[InnerOperation]
  ): Query[JsArray]

  def deleteOneRecord[ID: Put](
      model: PModel,
      primaryKeyValue: ID,
      innerReadOps: Vector[InnerOperation]
  ): Query[JsObject]

  def pushManyTo(
      model: PModel,
      field: PShapeField,
      items: Vector[JsValue],
      primaryKeyValue: Either[BigInt, String],
      innerReadOps: Vector[InnerOperation]
  ): Query[JsArray]

  def pushOneTo(
      model: PModel,
      field: PShapeField,
      item: JsValue,
      primaryKeyValue: Either[BigInt, String],
      innerReadOps: Vector[InnerOperation]
  ): Query[JsValue]

  def removeManyFrom(
      model: PModel,
      field: PShapeField,
      filter: QueryFilter,
      primaryKeyValue: Either[BigInt, String],
      innerReadOps: Vector[InnerOperation]
  ): Query[JsArray]

  def removeOneFrom(
      model: PModel,
      field: PShapeField,
      item: JsValue,
      primaryKeyValue: Either[BigInt, String],
      innerReadOps: Vector[InnerOperation]
  ): Query[JsValue]

  def readManyRecords(
      model: PModel,
      where: QueryWhere,
      innerReadOps: Vector[InnerOperation]
  ): Query[JsArray]

  def readOneRecord[ID: Put](
      model: PModel,
      primaryKeyValue: ID,
      innerReadOps: Vector[InnerOperation]
  ): Query[JsObject]
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
