package domain.primitives

case class CallExpression(callee: HFunctionValue, args: HObject)
    extends HExpression {
  override def eval(context: HObject): HValue =
    checkCache(context)(() => {
      val result = callee.body.eval(context ++ args)
      cache = Some(context -> result)
      result
    })
}
