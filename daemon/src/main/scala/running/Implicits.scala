package running

import pragma.domain._
import spray.json._
import cats.arrow.FunctionK
import scala.concurrent.Future
import cats.effect.IO
import cats.effect.ContextShift
import org.http4s._
import cats.Functor

object RunningImplicits {

  implicit def jsonFormatEntityDecoder[F[_]: Functor](implicit
      d: EntityDecoder[F, String]
  ): EntityDecoder[F, JsValue] = d.map(_.parseJson)

  implicit def jsonFormatEntityEncoder[F[_]: Functor](implicit
      d: EntityEncoder[F, String]
  ): EntityEncoder[F, JsValue] =
    d.contramap(_.compactPrint)

  implicit def futureFromIO(implicit cs: ContextShift[IO]) = new FunctionK[Future, IO] {
    def apply[A](f: Future[A]): IO[A] = IO.fromFuture(IO(f))
  }

  implicit object PValueJsonWriter extends JsonWriter[PValue] {
    override def write(obj: PValue): JsValue = obj match {
      case PStringValue(value) => JsString(value)
      case PIntValue(value)    => JsNumber(value)
      case PFloatValue(value)  => JsNumber(value)
      case PBoolValue(value)   => JsBoolean(value)
      case PDateValue(value)   => JsString(value.toString())
      case PArrayValue(values, _) =>
        JsArray(values.map(PValueJsonWriter.write(_)).toVector)
      case PFileValue(value, _) => JsString(value.toString())
      case PModelValue(value, _) =>
        JsObject(value.map { case (key, value) =>
          (key, PValueJsonWriter.write(value))
        })
      case PInterfaceValue(value, _) =>
        JsObject(value.map { case (key, value) =>
          (key, PValueJsonWriter.write(value))
        })
      case _: PFunctionValue =>
        throw new SerializationException(
          "Pragma functions are not serializable"
        )
      case POptionValue(value, _) =>
        value.map(PValueJsonWriter.write(_)).getOrElse(JsNull)
    }
  }
}
