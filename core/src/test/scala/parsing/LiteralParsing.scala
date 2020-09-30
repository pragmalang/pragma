package pragma.parsing

import pragma.domain._
import pragma.parsing._
import scala.util._
import org.scalatest.flatspec.AnyFlatSpec

class LiteralParsing extends AnyFlatSpec {
  "The literal: 123" should "be parsed as an PIntValue(123)" in {
    val result = new PragmaParser("123").literal.run()
    assert(result == Success(PIntValue(123)))
  }

  "The literal: 20.3" should "be parsed as an HFloatValue(20.3)" in {
    val result = new PragmaParser("20.3").literal.run()
    assert(result == Success(PFloatValue(20.3)))
  }

  "The literal: \"Hello \\\"Heavenly-x\\\"\"" should "be parsed as an PStringValue(Hello \\\"Heavenly-x\\\")" in {
    val result =
      new PragmaParser(""""Hello \"Heaveny-x\"""""").literal.run()
    assert(result == Success(PStringValue("Hello \"Heaveny-x\"")))
  }

  "The literal: true" should "be parsed as an PBoolValue(true)" in {
    val result = new PragmaParser("true").literal.run()
    assert(result == Success(PBoolValue(true)))
  }

  "The literal: [1, 2, 3]" should "be parsed as an PArrayValue(List(1, 2, 3))" in {
    val result = new PragmaParser("[1, 2, 3]").literal.run()
    val expected =
      PArrayValue(
        List(PIntValue(1), PIntValue(2), PIntValue(3)),
        PAny
      )
    assert(
      result.get
        .asInstanceOf[PArrayValue]
        .values == expected.values
    )
  }
}
