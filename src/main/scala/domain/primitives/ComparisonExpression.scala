package domain.primitives
import domain.ops.Comparisons

object ComparisonOperator extends Enumeration {
  val Gt, Gte, Lt, Lte, Eq, Neq = Value
}

case class ComparisonExpression(
    left: HExpression,
    op: ComparisonOperator.Value,
    right: HExpression
) extends HExpression {
  import ComparisonOperator._
  import Comparisons._
  override def eval(context: HObject): HValue = {
    val operation = op match {
      case Gt  => gt _
      case Gte => gte _
      case Lt  => lt _
      case Lte => lte _
      case Eq  => equ _
      case Neq => neq _
    }
    operation(left.eval(context), right.eval(context))
  }
}
