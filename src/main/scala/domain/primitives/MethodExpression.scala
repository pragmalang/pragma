package domain.primitives
import domain.utils._

case class MethodExpression(callee: HExpression, args: PositionalArgs) extends HExpression {
    override def eval(context: HObject): HValue = ???
}