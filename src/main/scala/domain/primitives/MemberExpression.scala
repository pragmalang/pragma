package domain.primitives
import domain._
import domain.utils._

case class MemberExpression(
    obj: HExpression,
    propName: String
) extends HExpression {
  override def eval(context: HObject): HValue = {
    val objValue = obj.eval(context)
    objValue match {
      case v: HModelValue => v.value(propName)
      case _ => throw new InternalException(s"")
    }
  }
}
