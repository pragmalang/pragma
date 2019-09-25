package domain.primitives
import domain.PositionRange

case class NativeCallExpression(
    callee: HOperation,
    args: List[HExpression],
    position: Option[PositionRange]
) extends HExpression {
  override def eval(context: HObject): HValue = checkCache(context)(() => {
    val result = callee(args.map(_.eval(context)))
    cache = Some(context -> result)
    result
  })
}
