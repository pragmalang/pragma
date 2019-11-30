package domain.primitives
import domain._
import domain.utils._

case class MemberExpression(
    obj: HExpression,
    propName: String,
    position: Option[PositionRange]
) extends HExpression {
  override def eval(context: HObject): HValue = {
    val objValue = obj.eval(context)
    objValue match {
      case v: HModelValue     => v.value(propName).eval(context)
      case v: HInterfaceValue => v.value(propName).eval(context)
      case v =>
        throw new TypeMismatchException(
          List(HModel("", Nil, Nil, None), HInterface("", Nil, None)),
          v.htype
        )
    }
  }
}
