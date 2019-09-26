package tests
import domain.primitives._
import org.scalatest._
import scala.collection.immutable.ListMap

class LogicalExpressionTests extends FlatSpec {
  "The boolean expression: true || !false && false" should "evaluate to true" in {
    val term1 =
      LogicalTerm(
        LogicalFactor.Expression(LiteralExpression(HBoolValue(true), None)),
        None
      )
    val term2 =
      LogicalTerm(
        LogicalFactor.Not(
          LogicalFactor.Expression(LiteralExpression(HBoolValue(false), None))
        ),
        Some(LogicalFactor.Expression(LiteralExpression(HBoolValue(false), None)))
      )
    val logicalExpr = LogicalExpression(term1, Some(term2), None)
    assert(logicalExpr.eval(ListMap.empty).asInstanceOf[HBoolValue].value)
  }
}

class ArithmeticExpressionTests extends FlatSpec {
  "The expression: 3 * 4 + 1" should "evaluate to 13" in {
    val term1 = ArithmeticTerm(
      ArithmeticFactor.Expression(LiteralExpression(HIntegerValue(3), None)),
      Some(
        (
          ArithmeticOperator.Mul,
          ArithmeticFactor.Expression(LiteralExpression(HIntegerValue(4), None))
        )
      )
    )
    val term2 = ArithmeticTerm(
      ArithmeticFactor.Expression(LiteralExpression(HIntegerValue(1), None)),
      None
    )
    val expr =
      ArithmeticExpression(term1, Some((ArithmeticOperator.Add, term2)), None)
    assert(expr.eval(ListMap.empty).asInstanceOf[HIntegerValue].value == 13L)
  }
}
