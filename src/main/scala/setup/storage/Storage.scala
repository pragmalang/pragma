package setup.storage

import sangria.ast.Document
import domain.SyntaxTree
import setup.utils._
import spray.json._
import scala.util._
import running.pipeline.Operation
import akka.stream.scaladsl.Source

trait Storage {
  def run(query: Document, operations: Vector[Operation]): Source[JsValue, _]
  def migrate(): Source[JsValue, _] = Source.empty
  def bootstrap: Try[Unit]
  val dockerComposeYaml: DockerCompose
}

case class MockStorage(syntaxTree: SyntaxTree) extends Storage {
  override def run(
      query: Document,
      operations: Vector[Operation]
  ): Source[JsValue, _] =
    Source.fromIterator { () =>
      List {
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
      }.iterator
    }

  override def bootstrap: Try[Unit] = Success(())

  override val dockerComposeYaml: DockerCompose = null
}
