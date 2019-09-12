package domain.primitives
import domain.utils._

sealed trait LogicalFactor {
  def eval(context: HObject): Boolean
}
object LogicalFactor {
  case class Not(factor: LogicalFactor) extends LogicalFactor {
    override def eval(context: HObject): Boolean =
      !factor.eval(context)
  }
  case class Expression(expr: HExpression) extends LogicalFactor {
    override def eval(context: HObject): Boolean =
      expr.eval(context) match {
        case bool: HBoolValue => bool.value
        case nonBool =>
          throw new TypeMismatchException(
            expected = HBool :: Nil,
            found = nonBool.htype
          )
      }
  }
}

case class LogicalTerm(left: LogicalFactor, right: Option[LogicalFactor]) {
  def eval(context: HObject): Boolean = right match {
    case Some(v) => v.eval(context) && left.eval(context)
    case None    => left.eval(context)
  }
}

case class LogicalExpression(left: LogicalTerm, right: Option[LogicalTerm])
    extends HExpression {
  override def eval(context: HObject): HValue =
    checkCache(context)(() => {
      val result = right match {
        case Some(v) => HBoolValue(v.eval(context) || left.eval(context))
        case None    => HBoolValue(left.eval(context))
      }
      cache = Some(context -> result)
      result
    })
}
