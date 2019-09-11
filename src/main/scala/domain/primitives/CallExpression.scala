package domain.primitives

case class CallExpression(callee: HFunctionValue, args: HObject) extends HExpression {
    override def eval(context: HObject): HValue = callee.body.eval(args)
}