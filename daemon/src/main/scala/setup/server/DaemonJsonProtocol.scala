package setup.server

import spray.json._
import pl.iterators.kebs.json._

package object DaemonJsonProtocol extends DefaultJsonProtocol with KebsSpray
