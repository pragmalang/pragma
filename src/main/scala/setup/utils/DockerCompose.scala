package setup.utils
import setup._

import Implicits._
import spray.json._
import spray.json.DefaultJsonProtocol._
import scala.collection.immutable.ListMap

case class DockerCompose(
    services: JsObject,
    volumes: Option[JsObject] = None,
    version: String = "3"
) {
  override def toString(): String =
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

  def +(that: DockerCompose): DockerCompose = DockerCompose(
    services = JsObject(that.services.fields ++ services.fields),
    volumes = (this.volumes, that.volumes) match {
      case (Some(thisVolumes), Some(thatVolumes)) =>
        Some(JsObject(thisVolumes.fields ++ thisVolumes.fields))
      case (Some(thisVolumes), None) => Some(thisVolumes)
      case (None, Some(thatVolumes)) => Some(thatVolumes)
      case _                         => None
    }
  )
}
