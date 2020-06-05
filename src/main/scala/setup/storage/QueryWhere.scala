package setup.storage

import spray.json._
import domain._
import cats.implicits._

case class QueryWhere(
    orderBy: Option[OrderBy],
    slice: Option[(Int, Int, Int)],
    filter: Option[QueryFilter]
) {
  def apply(objects: Vector[JsObject]) = {
    (orderBy, slice, filter) match {
      case (None, None, None) => objects
      case (Some(orderBy), Some(slice), Some(filter)) =>
        orderBy(objects.filter(filter))
          .slice(slice._1, slice._2)
          .filter(obj => objects.indexOf(obj) % slice._3 == 0)
      case (Some(orderBy), None, None) => orderBy(objects)
      case (None, Some(slice), None) =>
        objects
          .slice(slice._1, slice._2)
          .filter(obj => objects.indexOf(obj) % slice._3 == 0)
      case (None, None, Some(filter)) => objects.filter(filter)
      case (Some(orderBy), Some(slice), None) =>
        orderBy(objects)
          .slice(slice._1, slice._2)
          .filter(obj => objects.indexOf(obj) % slice._3 == 0)
      case (Some(orderBy), None, Some(filter)) =>
        orderBy(objects.filter(filter))
      case (None, Some(slice), Some(filter)) =>
        objects
          .filter(filter)
          .slice(slice._1, slice._2)
          .filter(obj => objects.indexOf(obj) % slice._3 == 0)
    }
  }
}

sealed trait Order
object Order {
  case object ASC extends Order
  case object DESC extends Order
}

// Only on PInt fields
case class OrderBy(field: PShapeField, order: Order = Order.ASC) {
  def apply(objects: Vector[JsObject]) =
    objects.sortBy(
      obj =>
        obj.fields.find(_._1 == field.id).get._2.asInstanceOf[JsNumber].value
    )
}

sealed trait QueryPredicate[T <: JsValue] extends (T => Boolean) {
  def apply(json: T): Boolean
}

final case class QueryFilter(
    predicate: QueryPredicate[JsValue],
    and: Option[QueryFilter],
    or: Option[QueryFilter],
    negate: Boolean = false
) extends QueryPredicate[JsValue] {
  override def apply(json: JsValue): Boolean =
    ((and, or) match {
      case (None, None)          => predicate(json)
      case (Some(and), Some(or)) => (predicate(json) && and(json)) || or(json)
      case (None, Some(or))      => predicate(json) || or(json)
      case (Some(and), None)     => predicate(json) && and(json)
    }) && !negate
}

final case class ModelQueryPredicate(
    model: PModel,
    fieldPredicates: Map[String, QueryPredicate[JsValue]]
) extends QueryPredicate[JsObject] {
  override def apply(json: JsObject): Boolean =
    json.fields
      .map(field => (field._2, fieldPredicates(field._1)))
      .map(f => f._2.apply(f._1))
      .foldLeft(true) {
        case (acc, result) => acc && result
      }
}

final case class ArrayQueryPredicate(length: NumberQueryPredicate)
    extends QueryPredicate[JsArray] {
  override def apply(json: JsArray): Boolean =
    length(JsNumber(json.elements.length))
}

final case class NumberQueryPredicate(
    lt: Float,
    lte: Float,
    eq: Float,
    gt: Float,
    gte: Float
) extends QueryPredicate[JsNumber] {
  override def apply(json: JsNumber): Boolean = {
    val value: BigDecimal = json.value
    value < lt && value <= lte && value == eq && value > gt && value >= gte
  }
}

final case class StringQueryPredicate(
    length: Int,
    startsWith: String,
    endsWith: String,
    matches: String
) extends QueryPredicate[JsString] {
  override def apply(json: JsString): Boolean =
    json.value.length == length && json.value.startsWith(startsWith) && json.value
      .endsWith(endsWith) && json.value.matches(matches)
}

final case class EnumQueryPredicate(value: String)
    extends QueryPredicate[JsString] {
  override def apply(json: JsString): Boolean = json.value == value
}
