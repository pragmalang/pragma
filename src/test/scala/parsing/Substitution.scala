package parsing

import org.scalatest.FlatSpec
import parsing.Substitutor
import domain.{HImport, GraalFunction}
import spray.json._
import scala.util.Success

class Substitution extends FlatSpec {

  "Substitution function readGraalFunctions" should "return an object containing all defined functions in a file as GraalFunctionValues" in {
    val himport =
      HImport("functions", "./src/test/scala/parsing/test-functions.js", None)
    val functionObject = Substitutor.readGraalFunctions(himport).get
    val f = functionObject("f").asInstanceOf[GraalFunction]
    val additionResult = f.execute(JsNumber(2))
    assert(
      additionResult.get.asInstanceOf[JsNumber].value == BigDecimal(Success(3.0).value)
    )
  }

}
