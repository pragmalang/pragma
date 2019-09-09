package domain.primitives
import domain.ops.comparisons

object ComparisonOperator extends Enumeration {
  val Gt, Gte, Lt, Lte, Eq, Neq = Value
}

case class ComparisonExpression(
    left: HExpression,
    op: ComparisonOperator.Value,
    right: HExpression
) extends HExpression {
  import ComparisonOperator._
  override def eval(context: HObject): HValue = op match {
    case Gt  => comparisons.Gt(left.eval(context), right.eval(context))
    case Gte => comparisons.Gte(left.eval(context), right.eval(context))
    case Lt  => comparisons.Lt(left.eval(context), right.eval(context))
    case Lte => comparisons.Lte(left.eval(context), right.eval(context))
    case Eq  => comparisons.Eq(left.eval(context), right.eval(context))
    case Neq => comparisons.Neq(left.eval(context), right.eval(context))
  }
}
