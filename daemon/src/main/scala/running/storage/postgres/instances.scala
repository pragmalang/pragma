package running.storage.postgres

import pragma.domain.utils._
import cats._
import spray.json._
import java.util.UUID

package object instances {

  implicit val jsObjectRead: doobie.Read[JsObject] =
    new doobie.Read[JsObject](Nil, (resultSet, _) => {
      val rsMetadata = resultSet.getMetaData
      val columnCount = rsMetadata.getColumnCount
      val keys = (1 to columnCount).map(rsMetadata.getColumnLabel).toVector
      val mapBuilder = Map.newBuilder[String, JsValue]
      for (columnIndex <- 1 to columnCount) {
        val key = keys(columnIndex - 1)
        val value = columnValueToJson(resultSet.getObject(columnIndex))
        mapBuilder += (key -> value)
      }
      JsObject(mapBuilder.result)
    })

  /** Used to get table columns as JSON
    * CAUTION: Returns JsNull if the input value's type
    * doesn't match any case.
    */
  private def columnValueToJson(value: Any): JsValue = value match {
    case null       => JsNull
    case i: Int     => JsNumber(i)
    case d: Double  => JsNumber(d)
    case s: String  => JsString(s)
    case true       => JsTrue
    case false      => JsFalse
    case d: Date    => JsString(d.toString)
    case s: Short   => JsNumber(s.toDouble)
    case l: Long    => JsNumber(l)
    case f: Float   => JsNumber(f.toDouble)
    case uuid: UUID => JsString(uuid.toString)
    case _          => JsNull
  }

}
