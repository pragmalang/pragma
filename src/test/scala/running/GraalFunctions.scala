package running

import org.graalvm.polyglot._
import domain.Implicits.GraalValueJsonFormater
import org.scalatest.FlatSpec
import spray.json._

class GraalFunctions extends FlatSpec {
  "GraalValueJsonFormater" should "read and write Graal values correctly" in {
    val ctx = Context.create()
    // ctx.eval("python", "def f(d): return d['id']")
    // ctx.eval("js", "const g = o => o.name")

    val graalValue = ctx.eval(
      "js",
      """
    ({
        id: 123, 
        name: 'Stan Smith',
        pets: [
            'Haley',
            'Roger the alien'
        ],
        friends: null
    })
    """
    )
    val json = GraalValueJsonFormater.write(graalValue)
    val expected = JsObject(
      Map(
        "id" -> JsNumber(123.0),
        "name" -> JsString("Stan Smith"),
        "pets" -> JsArray(
          Vector(JsString("Haley"), JsString("Roger the alien"))
        ),
        "friends" -> JsNull
      )
    )
    assert(json == expected)

    val originalGraalValue = GraalValueJsonFormater.read(json)
    val originalAsMap = originalGraalValue.asHostObject
      .asInstanceOf[java.util.HashMap[String, Value]]
    json match {
      case JsObject(fields) => {
        fields("name") match {
          case JsString(value) =>
            assert(value == originalAsMap.get("name").asString)
          case _ => fail()
        }

        assert(fields("friends") == JsNull)

        fields("pets") match {
          case JsArray(values) =>
            assert(
              originalAsMap
                .get("pets")
                .asHostObject
                .asInstanceOf[Array[Value]](1)
                .asString
                .contains(
                  values(1)
                    .asInstanceOf[JsString]
                    .value
                )
            )
          case _ => fail()
        }
      }
      case _ => fail()
    }
  }
}
