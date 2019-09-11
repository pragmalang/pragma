package domain.primitives

case class MethodExpression1(callee: HOperation1, arg: HExpression)
    extends HExpression {
  override def eval(context: HObject): HValue = callee(arg.eval(context))
}

case class MethodExpression2(
    callee: HOperation2,
    arg1: HExpression,
    arg2: HExpression
) extends HExpression {
  override def eval(context: HObject): HValue =
    callee(arg1.eval(context), arg2.eval(context))
}
