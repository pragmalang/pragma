package running.operations

import domain._
import java.time.ZonedDateTime

case class QueryAgg(
    filter: Seq[QueryFilter],
    from: Option[Int],
    to: Option[Int]
)

case class QueryFilter(
    predicate: QueryPredicate,
    and: Seq[QueryFilter],
    or: Seq[QueryFilter],
    negated: Boolean
)

sealed trait QueryPredicate

case class NullPredicate(isNull: Boolean) extends QueryPredicate

case class StringPredicate(
    length: Option[IntPredicate],
    startsWith: Option[String],
    endsWith: Option[String],
    pattern: Option[String]
) extends QueryPredicate

case class IntPredicate(
    lt: Option[Int],
    gt: Option[Int],
    eq: Option[Int],
    gte: Option[Int],
    lte: Option[Int]
) extends QueryPredicate

case class FloatPredicate(
    lt: Option[Double],
    gt: Option[Double],
    eq: Option[Double],
    gte: Option[Double],
    lte: Option[Double]
) extends QueryPredicate

case class BoolPredicate(is: Boolean) extends QueryPredicate

case class DatePredicate(
    before: Option[ZonedDateTime],
    after: Option[ZonedDateTime],
    eq: Option[ZonedDateTime]
) extends QueryPredicate

case class ModelPredicate(
    model: PModel,
    fieldPredicates: Seq[(PModelField, QueryPredicate)]
) extends QueryPredicate

case class ArrayPredicate(length: IntPredicate) extends QueryPredicate
