package domain.primitives
import domain.utils._
import domain.ops.arithmetics
import domain.PositionRange

object ArithmeticOperator extends Enumeration {
  val Add, Sub, Mul, Div, Mod = Value
}

sealed trait ArithmeticFactor {
  def eval(context: HObject): HValue
}
object ArithmeticFactor {
  case class Negative(factor: ArithmeticFactor) extends ArithmeticFactor {
    def eval(context: HObject): HValue = factor.eval(context) match {
      case HIntegerValue(v) => HIntegerValue(-v)
      case HFloatValue(v)   => HFloatValue(-v)
      case nonNumeric =>
        throw new TypeMismatchException(
          List(HInteger, HFloat),
          nonNumeric.htype
        )
    }
  }
  case class Expression(expr: HExpression) extends ArithmeticFactor {
    def eval(context: HObject): HValue = expr.eval(context)
  }
}
case class ArithmeticTerm(
    left: ArithmeticFactor,
    right: Option[(ArithmeticOperator.Value, ArithmeticFactor)]
) {
  import ArithmeticOperator._
  def eval(context: HObject): HValue = right match {
    case None => left.eval(context)
    case Some((Mul, right)) =>
      arithmetics.Mul(List(left.eval(context), right.eval(context)))
    case Some((Div, right)) =>
      arithmetics.Div(List(left.eval(context), right.eval(context)))
    case Some((Mod, right)) =>
      arithmetics.Mod(List(left.eval(context), right.eval(context)))
    case Some((nonMultiplication, right)) =>
      throw new InternalException(
        s"Term should concist of <left (Mul, Div, or Mod) right>, but $nonMultiplication found"
      )
  }
}

case class ArithmeticExpression(
    left: ArithmeticTerm,
    right: Option[(ArithmeticOperator.Value, ArithmeticTerm)],
    position: Option[PositionRange]
) extends HExpression {
  import ArithmeticOperator._
  override def eval(context: HObject): HValue =
    checkCache(context)(
      () =>
        right match {
          case None => left.eval(context)
          case Some((Add, right)) =>
            arithmetics.Add(List(left.eval(context), right.eval(context)))
          case Some((Sub, right)) =>
            arithmetics.Sub(List(left.eval(context), right.eval(context)))
          case Some((nonAddition, _)) =>
            throw new InternalException(
              s"Term should consist of <left (Add or Sub) right>, but $nonAddition found"
            )
        }
    )
}
