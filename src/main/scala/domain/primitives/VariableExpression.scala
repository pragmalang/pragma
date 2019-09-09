package domain.primitives
import domain._
import domain.utils._

case class VariableExpression(id: String, htype: HType)
    extends HExpression
    with Identifiable {
  override def eval(context: HObject): HValue = context(id)
}
