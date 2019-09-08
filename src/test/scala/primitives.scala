package tests
import domain.primitives._
import org.scalatest._

class LogicalExpressionTests extends FlatSpec  {
  "The boolean expression: true || !false && false" should "evaluate to true" in {
    val term1 =
      LogicalTerm(ExprFactor(LiteralExpression(HBoolValue(true))), None)
    val term2 =
      LogicalTerm(
        NotFactor(ExprFactor(LiteralExpression(HBoolValue(false)))),
        Some(ExprFactor(LiteralExpression(HBoolValue(false))))
      )
    val logicalExpr = LogicalExpression(term1, Some(term2))
    assert(logicalExpr.eval(Map.empty).asInstanceOf[HBoolValue].value)
  }
}
