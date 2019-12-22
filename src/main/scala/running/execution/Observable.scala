package running.execution

import running.pipeline.Response
import running.utils._

import akka.stream.scaladsl.Source
import akka.stream.Materializer
import scala.util.{Random, Try, Success, Failure}

case class Observable[T]() {
  import Observable._

  var emittedValues = List.empty[T]

  def source = Source.fromIterator(() => emittedValues.iterator)

  // User custom dispatcher as an execution context
  // See: https://doc.akka.io/docs/akka-http/current/handling-blocking-operations-in-akka-http-routes.html?language=scala
  def subscribe(subscriber: T => Unit)(implicit materializer: Materializer) =
    source.runForeach(subscriber)(materializer)

  def emit(value: T) = {
    emittedValues = emittedValues :+ value
  }
}
