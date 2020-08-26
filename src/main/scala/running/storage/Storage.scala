package running.storage

import running._
import spray.json._
import scala.util._
import domain._
import cats.Monad

class Storage[S, M[_]: Monad](
    val queryEngine: QueryEngine[S, M],
    val migrationEngine: MigrationEngine[S, M]
) {

  def run(
      operations: Operations.OperationsMap
  ): M[queryEngine.TransactionResultMap] =
    queryEngine.run(operations)

  def migrate: M[Either[Throwable, Unit]] =
    migrationEngine.migrate

}

trait MigrationEngine[S, M[_]] {
  def migrate: M[Either[Throwable, Unit]]
}

case class MigrationError(step: MigrationStep) extends Exception

abstract class QueryEngine[S, M[_]: Monad] {
  type Query[_]

  /** Succeeds only if all operations do */
  final type TransactionResultMap =
    Vector[
      (
          Option[Operations.OperationGroupName],
          Vector[
            (Operations.ModelSelectionName, Vector[(Operation, JsValue)])
          ]
      )
    ]

  def run(
      operations: Operations.OperationsMap
  ): M[TransactionResultMap]

  def query(op: Operation): Query[JsValue]

  def runQuery[A](query: Query[A]): M[A]

  def createManyRecords(
      model: PModel,
      records: Vector[JsObject],
      innerReadOps: Vector[InnerOperation]
  ): Query[Vector[JsObject]]

  def createOneRecord(
      model: PModel,
      record: JsObject,
      innerReadOps: Vector[InnerOperation]
  ): Query[JsObject]

  def updateManyRecords(
      model: PModel,
      recordsWithIds: Vector[ObjectWithId],
      innerReadOps: Vector[InnerOperation]
  ): Query[JsArray]

  def updateOneRecord(
      model: PModel,
      primaryKeyValue: JsValue,
      newRecord: JsObject,
      innerReadOps: Vector[InnerOperation]
  ): Query[JsObject]

  def deleteManyRecords(
      model: PModel,
      primaryKeyValues: Vector[JsValue],
      innerReadOps: Vector[InnerOperation]
  ): Query[JsArray]

  def deleteOneRecord(
      model: PModel,
      primaryKeyValue: JsValue,
      innerReadOps: Vector[InnerOperation],
      cascade: Boolean
  ): Query[JsObject]

  def pushManyTo(
      model: PModel,
      field: PShapeField,
      items: Vector[JsValue],
      primaryKeyValue: JsValue,
      innerReadOps: Vector[InnerOperation]
  ): Query[JsObject]

  def pushOneTo(
      model: PModel,
      field: PShapeField,
      item: JsValue,
      sourceId: JsValue,
      innerReadOps: Vector[InnerOperation]
  ): Query[JsObject]

  def removeManyFrom(
      model: PModel,
      arrayField: PShapeField,
      sourcePkValue: JsValue,
      targetPkValues: Vector[JsValue],
      innerReadOps: Vector[InnerOperation]
  ): Query[JsObject]

  def removeOneFrom(
      model: PModel,
      arrayField: PShapeField,
      sourcePkValue: JsValue,
      targetPkValue: JsValue,
      innerReadOps: Vector[InnerOperation]
  ): Query[JsObject]

  def readManyRecords(
      model: PModel,
      where: QueryWhere,
      innerReadOps: Vector[InnerOperation]
  ): Query[JsArray]

  def readOneRecord(
      model: PModel,
      primaryKeyValue: JsValue,
      innerReadOps: Vector[InnerOperation]
  ): Query[JsObject]

}
