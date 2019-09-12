package domain.primitives

case class NativeCallExpression(
    callee: HOperation,
    args: List[HExpression]
) extends HExpression {
  override def eval(context: HObject): HValue = checkCache(context)(() => {
    val result = callee(args.map(_.eval(context)))
    cache = Some(context -> result)
    result
  })
}
