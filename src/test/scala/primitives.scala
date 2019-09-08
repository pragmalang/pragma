package tests
import domain.primitives._
import org.scalatest._

class LogicalExpressionTests extends FlatSpec {
  "The boolean expression: true || !false && false" should "evaluate to true" in {
    val term1 =
      LogicalTerm(
        LogicalFactor.Expression(LiteralExpression(HBoolValue(true))),
        None
      )
    val term2 =
      LogicalTerm(
        LogicalFactor.Not(
          LogicalFactor.Expression(LiteralExpression(HBoolValue(false)))
        ),
        Some(LogicalFactor.Expression(LiteralExpression(HBoolValue(false))))
      )
    val logicalExpr = LogicalExpression(term1, Some(term2))
    assert(logicalExpr.eval(Map.empty).asInstanceOf[HBoolValue].value)
  }
}
