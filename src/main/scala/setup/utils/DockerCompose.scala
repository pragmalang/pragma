package setup.utils
import setup._

import domain.Implicits._
import Implicits._
import spray.json._
import spray.json.DefaultJsonProtocol._
import scala.collection.immutable.ListMap

case class DockerCompose(
    services: JsObject,
    volumes: Option[JsObject] = None,
    version: String = "3"
) {

  def render: String =
    JsObject(
      volumes match {
        case None =>
          ListMap(
            "version" -> version.toJson,
            "services" -> services
          )
        case Some(volumes) =>
          ListMap(
            "version" -> version.toJson,
            "services" -> services,
            "volumes" -> volumes
          )
      }
    ).renderYaml()
}
