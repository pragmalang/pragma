package setup.storage

import domain.{PModel, SyntaxTree}
import running.pipeline.Operation
import spray.json._
import scala.util.{Try, Success}
import sangria.ast.Document
import setup.MigrationStep

import concurrent.Future
import concurrent.ExecutionContext.Implicits.global

case class MockStorage(syntaxTree: SyntaxTree) extends Storage {

  override def modelEmpty(model: PModel): Future[Boolean] = Future(true)
  override def modelExists(model: PModel): Future[Boolean] = Future(true)

  override def run(
      query: Document,
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
  ): Future[Vector[Try[Unit]]] = Future(Vector.empty)
}
