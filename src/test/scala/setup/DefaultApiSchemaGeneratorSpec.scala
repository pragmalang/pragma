package setup

import org.scalatest._
import sangria.renderer.QueryRenderer
import setup.DefualtApiSchemaGenerator._

class DefaultApiSchemaGeneratorSpec extends FlatSpec {
  "outputTypes" should "work" in {
    println(QueryRenderer.render(DefaultApiSchemaGenerator(MockSyntaxTree.syntaxTree).subscriptionType))
  }
}
