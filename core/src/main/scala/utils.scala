package pragma

import spray.json._
import pl.iterators.kebs.json._

object utils {
  object JsonCodec extends DefaultJsonProtocol with KebsSpray
}

sealed trait RunMode
object RunMode {
  case object Dev extends RunMode
  case object Prod extends RunMode
}
