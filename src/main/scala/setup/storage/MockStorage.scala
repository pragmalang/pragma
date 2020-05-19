package setup.storage

import running.pipeline.Operation
import spray.json._
import setup.MigrationStep
import cats.instances.future._

import concurrent.Future
import concurrent.ExecutionContext.Implicits.global
import domain.{PModel, PShapeField}
import running.pipeline.InnerOperation
import scala.util.Try

object MockMigrationEngine extends MigrationEngine[MockStorage.type, Future] {
  def migrate(
      migrationSteps: Vector[MigrationStep]
  ): Future[Vector[Try[Unit]]] = Future(Vector.empty)
}

object MockQueryEngine extends QueryEngine[MockStorage.type, Future] {
  def run(
      operations: Map[Option[String], Vector[Operation]]
  ): Future[Either[JsObject, Vector[JsObject]]] =
    Future(
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

  def createManyRecords(
      model: PModel,
      records: Vector[JsObject],
      innerReadOps: Vector[InnerOperation]
  ): Future[JsArray] = ???

  def createOneRecord(
      model: PModel,
      record: JsObject,
      innerReadOps: Vector[InnerOperation]
  ): Future[JsObject] = ???

  def updateManyRecords(
      model: PModel,
      recordsWithIds: Vector[JsObject],
      innerReadOps: Vector[InnerOperation]
  ): Future[JsArray] = ???

  def updateOneRecord(
      model: PModel,
      primaryKeyValue: Either[BigInt, String],
      newRecord: JsObject,
      innerReadOps: Vector[InnerOperation]
  ): Future[JsObject] = ???

  def deleteManyRecords(
      model: PModel,
      filter: Either[QueryFilter, Vector[Either[String, BigInt]]],
      innerReadOps: Vector[InnerOperation]
  ): Future[JsArray] = ???

  def deleteOneRecord(
      model: PModel,
      primaryKeyValue: Either[BigInt, String],
      innerReadOps: Vector[InnerOperation]
  ): Future[JsObject] = ???

  def pushManyTo(
      model: PModel,
      field: PShapeField,
      items: Vector[JsValue],
      primaryKeyValue: Either[BigInt, String],
      innerReadOps: Vector[InnerOperation]
  ): Future[JsArray] = ???

  def pushOneTo(
      model: PModel,
      field: PShapeField,
      item: JsValue,
      primaryKeyValue: Either[BigInt, String],
      innerReadOps: Vector[InnerOperation]
  ): Future[JsValue] = ???

  def removeManyFrom(
      model: PModel,
      field: PShapeField,
      filter: QueryFilter,
      primaryKeyValue: Either[BigInt, String],
      innerReadOps: Vector[InnerOperation]
  ): Future[JsArray] = ???

  def removeOneFrom(
      model: PModel,
      field: PShapeField,
      item: JsValue,
      primaryKeyValue: Either[BigInt, String],
      innerReadOps: Vector[InnerOperation]
  ): Future[JsValue] = ???

  def readManyRecords(
      model: PModel,
      where: QueryWhere,
      innerReadOps: Vector[InnerOperation]
  ): Future[JsArray] = ???

  def readOneRecord(
      model: PModel,
      primaryKeyValue: Either[BigInt, String],
      innerReadOps: Vector[InnerOperation]
  ): Future[JsObject] = ???
}

object MockStorage {
  val storage = new Storage(MockQueryEngine, MockMigrationEngine)
}
