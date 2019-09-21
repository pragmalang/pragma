import org.scalatest._
import domain._
import domain.primitives._
import parsing._
import scala.util._

class LiteralParsing extends FlatSpec {
  "The literal: 123" should "be parsed as an HIntegerValue(123)" in {
    val result = new HeavenlyParser("123").literal.run()
    assert(result == Success(HIntegerValue(123)))
  }

  "The literal: 20.3" should "be parsed as an HFloatValue(20.3)" in {
    val result = new HeavenlyParser("20.3").literal.run()
    assert(result == Success(HFloatValue(20.3)))
  }

  "The literal: \"Hello \\\"Heavenly-x\\\"\"" should "be parsed as an HStringValue(Hello \\\"Heavenly-x\\\")" in {
    val result =
      new HeavenlyParser(""""Hello \"Heaveny-x\"""""").literal.run()
    assert(result == Success(HStringValue("Hello \"Heaveny-x\"")))
  }

  "The literal: true" should "be parsed as an HBoolValue(true)" in {
    val result = new HeavenlyParser("true").literal.run()
    assert(result == Success(HBoolValue(true)))
  }

  "The literal: [1, 2, 3]" should "be parsed as an HArrayValue(List(1, 2, 3))" in {
    val result = new HeavenlyParser("[1, 2, 3]").literal.run()
    val expected =
      HArrayValue(
        List(HIntegerValue(1), HIntegerValue(2), HIntegerValue(3)),
        new HType {}
      )
    assert(
      result.get
        .asInstanceOf[HArrayValue[HIntegerValue]]
        .values == expected.values
    )
  }
}
