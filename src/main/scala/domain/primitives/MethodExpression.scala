package domain.primitives
import domain.utils._

case class MethodExpression(callee: HOperation, args: List[HExpression]) extends HExpression {
    override def eval(context: HObject): HValue = ???
}