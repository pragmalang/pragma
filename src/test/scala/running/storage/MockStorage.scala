package running.storage

import running.Operation
import spray.json._
import running.storage.MigrationStep
import cats._
import cats.implicits._

import concurrent.Future
import concurrent.ExecutionContext.Implicits.global
import domain.{PModel, PShapeField}
import running.InnerOperation
import scala.util.Try
import running.ObjectWithId

object MockMigrationEngine extends MigrationEngine[MockStorage.type, Future] {
  def migrate(
      migrationSteps: Vector[MigrationStep]
  ): Future[Vector[Try[Unit]]] = Future(Vector.empty)
}

object MockQueryEngine extends QueryEngine[MockStorage.type, Future] {
  override type Query[A] = Id[A]

  def run(
      operations: Map[Option[String], Vector[Operation]]
  ): Future[JsObject] =
    Future(
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

  def createManyRecords(
      model: PModel,
      records: Vector[JsObject],
      innerReadOps: Vector[InnerOperation]
  ): Vector[JsObject] = ???

  def createOneRecord(
      model: PModel,
      record: JsObject,
      innerReadOps: Vector[InnerOperation]
  ): JsObject = ???

  def updateManyRecords(
      model: PModel,
      recordsWithIds: Vector[ObjectWithId],
      innerReadOps: Vector[InnerOperation]
  ): JsArray = ???

  def updateOneRecord(
      model: PModel,
      primaryKeyValue: JsValue,
      newRecord: JsObject,
      innerReadOps: Vector[InnerOperation]
  ): JsObject = ???

  def deleteManyRecords(
      model: PModel,
      primaryKeyValues: Vector[JsValue],
      innerReadOps: Vector[InnerOperation]
  ): JsArray = ???

  def deleteOneRecord(
      model: PModel,
      primaryKeyValue: JsValue,
      innerReadOps: Vector[InnerOperation],
      cascade: Boolean
  ): JsObject = ???

  def pushManyTo(
      model: PModel,
      field: PShapeField,
      items: Vector[JsValue],
      primaryKeyValue: JsValue,
      innerReadOps: Vector[InnerOperation]
  ): JsObject = ???

  def pushOneTo(
      model: PModel,
      field: PShapeField,
      item: JsValue,
      primaryKeyValue: JsValue,
      innerReadOps: Vector[InnerOperation]
  ): JsObject = ???

  def removeManyFrom(
      model: PModel,
      arrayField: PShapeField,
      sourcePkValue: JsValue,
      targetPkValues: Vector[JsValue],
      innerReadOps: Vector[InnerOperation]
  ): JsObject = ???

  def removeOneFrom(
      model: PModel,
      field: PShapeField,
      item: JsValue,
      primaryKeyValue: JsValue,
      innerReadOps: Vector[InnerOperation]
  ): JsObject = ???

  def readManyRecords(
      model: PModel,
      where: QueryWhere,
      innerReadOps: Vector[InnerOperation]
  ): JsArray = ???

  def readOneRecord(
      model: PModel,
      primaryKeyValue: JsValue,
      innerReadOps: Vector[InnerOperation]
  ): JsObject = ???
}

object MockStorage {
  val storage = new Storage(MockQueryEngine, MockMigrationEngine)
}
