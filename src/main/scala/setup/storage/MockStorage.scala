package setup.storage

import running.pipeline.Operation
import spray.json._
import setup.MigrationStep
import cats._
import cats.implicits._

import concurrent.Future
import concurrent.ExecutionContext.Implicits.global
import domain.{PModel, PShapeField}
import running.pipeline.InnerOperation
import scala.util.Try
import doobie.util.Put

object MockMigrationEngine extends MigrationEngine[MockStorage.type, Future] {
  def migrate(
      migrationSteps: Vector[MigrationStep]
  ): Future[Vector[Try[Unit]]] = Future(Vector.empty)
}

object MockQueryEngine extends QueryEngine[MockStorage.type, Future] {
  override type Query[A] = Id[A]

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
  ): JsArray = ???

  def createOneRecord(
      model: PModel,
      record: JsObject,
      innerReadOps: Vector[InnerOperation]
  ): JsObject = ???

  def updateManyRecords(
      model: PModel,
      recordsWithIds: Vector[JsObject],
      innerReadOps: Vector[InnerOperation]
  ): JsArray = ???

  def updateOneRecord(
      model: PModel,
      primaryKeyValue: Either[Long, String],
      newRecord: JsObject,
      innerReadOps: Vector[InnerOperation]
  ): JsObject = ???

  def deleteManyRecords(
      model: PModel,
      filter: Either[QueryFilter, Vector[Either[String, Long]]],
      innerReadOps: Vector[InnerOperation]
  ): JsArray = ???

  def deleteOneRecord[ID: Put](
      model: PModel,
      primaryKeyValue: ID,
      innerReadOps: Vector[InnerOperation]
  ): JsObject = ???

  def pushManyTo(
      model: PModel,
      field: PShapeField,
      items: Vector[JsValue],
      primaryKeyValue: Either[Long, String],
      innerReadOps: Vector[InnerOperation]
  ): JsArray = ???

  def pushOneTo(
      model: PModel,
      field: PShapeField,
      item: JsValue,
      primaryKeyValue: Either[Long, String],
      innerReadOps: Vector[InnerOperation]
  ): JsValue = ???

  def removeManyFrom(
      model: PModel,
      field: PShapeField,
      filter: QueryFilter,
      primaryKeyValue: Either[Long, String],
      innerReadOps: Vector[InnerOperation]
  ): JsArray = ???

  def removeOneFrom(
      model: PModel,
      field: PShapeField,
      item: JsValue,
      primaryKeyValue: Either[Long, String],
      innerReadOps: Vector[InnerOperation]
  ): JsValue = ???

  def readManyRecords(
      model: PModel,
      where: QueryWhere,
      innerReadOps: Vector[InnerOperation]
  ): JsArray = ???

  def readOneRecord[ID: Put](
      model: PModel,
      primaryKeyValue: ID,
      innerReadOps: Vector[InnerOperation]
  ): JsObject = ???
}

object MockStorage {
  val storage = new Storage(MockQueryEngine, MockMigrationEngine)
}
