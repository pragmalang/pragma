package domain.primitives
import domain.PositionRange

case class CallExpression(
    callee: HFunctionValue,
    args: HObject,
    position: Option[PositionRange]
) extends HExpression {
  override def eval(context: HObject): HValue =
    checkCache(context)(() => {
      val result = callee.body.eval(context ++ args)
      cache = Some(context -> result)
      result
    })
}
