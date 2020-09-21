package running.operations

import domain._, domain.utils._
import java.util.Date

trait QueryAgg[P <: QueryPredicate, QF <: QueryFilter[P]] {
  val filter: Seq[QF]
  val from: Option[Int]
  val to: Option[Int]
}

case class ModelAgg(
    targetModel: PModel,
    filter: Seq[ModelFilter],
    from: Option[Int],
    to: Option[Int]
) extends QueryAgg[ModelPredicate, ModelFilter]

case class ArrayFieldAgg(
    parentModel: PModel,
    field: PModelField,
    filter: Seq[ArrayFieldFilter],
    from: Option[Int],
    to: Option[Int]
) extends QueryAgg[QueryPredicate, ArrayFieldFilter]

trait QueryFilter[P <: QueryPredicate] {
  val predicate: P
  val and: Seq[QueryFilter[P]]
  val or: Seq[QueryFilter[P]]
  val negated: Boolean
}

case class ModelFilter(
    predicate: ModelPredicate,
    and: Seq[ModelFilter],
    or: Seq[ModelFilter],
    negated: Boolean
) extends QueryFilter[ModelPredicate]

case class ArrayFieldFilter(
    field: PModelField,
    predicate: QueryPredicate,
    and: Seq[ArrayFieldFilter],
    or: Seq[ArrayFieldFilter],
    negated: Boolean
) extends QueryFilter[QueryPredicate]

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
    before: Option[Date],
    after: Option[Date],
    eq: Option[Date]
) extends QueryPredicate

case class ModelPredicate(
    model: PModel,
    fieldPredicates: Seq[(FieldId, QueryPredicate)]
) extends QueryPredicate

case class ArrayPredicate(length: IntPredicate) extends QueryPredicate
