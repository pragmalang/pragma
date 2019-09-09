package domain.primitives
import domain.utils._
import domain.ops

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
    case None               => left.eval(context)
    case Some((Mul, right)) => ops.Mul(left.eval(context), right.eval(context))
    case Some((Div, right)) => ops.Div(left.eval(context), right.eval(context))
    case Some((Mod, right)) => ops.Mod(left.eval(context), right.eval(context))
    case Some((nonMultiplication, right)) =>
      throw new InternalException(
        s"Term should concist of <left (Mul, Div, or Mod) right>, but $nonMultiplication found"
      )
  }
}

case class ArithmeticExpression(
    left: ArithmeticTerm,
    right: Option[(ArithmeticOperator.Value, ArithmeticTerm)]
) extends HExpression {
  import ArithmeticOperator._
  override def eval(context: HObject): HValue = right match {
    case None               => left.eval(context)
    case Some((Add, right)) => ops.Add(left.eval(context), right.eval(context))
    case Some((Sub, right)) => ops.Sub(left.eval(context), right.eval(context))
    case Some((nonAddition, _)) =>
      throw new InternalException(
        s"Term should concist of <left (Add or Sub) right>, but $nonAddition found"
      )
  }
}
