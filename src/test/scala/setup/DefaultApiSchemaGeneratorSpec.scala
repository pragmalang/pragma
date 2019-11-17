package setup

import org.scalatest._
import sangria.renderer.QueryRenderer
import setup.DefualtApiSchemaGenerator._
import sangria.ast.Document
import sangria.macros._
class DefaultApiSchemaGeneratorSpec extends FunSuite {
  test("outputTypes method on DefaultApiSchemaGenerator works") {
    val expected = gql"""
    type Business {
      username: String!
      email: String!
      password: String!
      branches(where: WhereInput): [Branch]!
      businessType: BusinessType!
    }
    
    type Branch {
      address: String!
      business: Business!
    }
    
    enum BusinessType {
      FOOD
      CLOTHING
      OTHER
    }
    """.renderPretty

    val generatedTypes =
      Document(
        DefaultApiSchemaGenerator(MockSyntaxTree.syntaxTree).outputTypes.toVector
      ).renderPretty

    assert(generatedTypes == expected)
  }
}
