package pragma
import spray.json._
import pl.iterators.kebs.json._

object utils {
  object JsonCodec extends DefaultJsonProtocol with KebsSpray
}
