package pragma.parsing

import pragma.domain._
import org.scalatest.flatspec.AnyFlatSpec

class ConfigParsing extends AnyFlatSpec {
  "Config block" should "be parsed correctly" in {
    val code = """config {
                      someKey = "some value"
                      someOtherKey = 42
                      projectName = "TEST"
                  }"""

    val entries = new PragmaParser(code).configDef.run().get.values

    assert(entries(0).key == "someKey")
    assert(entries(0).value == PStringValue("some value"))

    assert(entries(1).key == "someOtherKey")
    assert(entries(1).value == PIntValue(42L))
  }
}
