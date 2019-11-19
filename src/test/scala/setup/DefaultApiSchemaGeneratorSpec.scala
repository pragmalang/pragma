package setup

import org.scalatest._
import sangria.renderer.QueryRenderer
import setup.DefualtApiSchemaGenerator._
import sangria.ast.Document
import sangria.macros._
class DefaultApiSchemaGeneratorSpec extends FunSuite {
  val generator = DefaultApiSchemaGenerator(MockSyntaxTree.syntaxTree)

  test("outputTypes method works") {
    val expected = gql"""
    type Business {
      username: String
      email: String!
      password: String!
      branches(where: WhereInput): [Branch]!
      mainBranch: Branch
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
      Document(generator.outputTypes.toVector).renderPretty
    assert(generatedTypes == expected)
  }

  test("inputTypes(ObjectInput) method works") {
    val expected = gql"""
    input BusinessObjectInput {
      username: String
      email: String!
      password: String!
      branches: [BranchReferenceInput]!
      mainBranch: BranchReferenceInput
      businessType: BusinessType!
    }
    
    input BranchObjectInput {
      address: String!
      business: BusinessReferenceInput!
    }
    """.renderPretty

    val generatedTypes =
      Document(generator.inputTypes(ObjectInput).toVector).renderPretty
    assert(generatedTypes == expected)
  }

  test("inputTypes(OptionalInput) method works") {
    val expected = gql"""
    input BusinessOptionalInput {
      username: String
      email: String
      password: String
      branches: [BranchOptionalInput]
      mainBranch: BranchOptionalInput
      businessType: BusinessType
    }
    
    input BranchOptionalInput {
      address: String
      business: BusinessOptionalInput
    }
    """.renderPretty

    val generatedTypes =
      Document(generator.inputTypes(OptionalInput).toVector).renderPretty
    assert(generatedTypes == expected)
  }

  test("inputTypes(ReferenceInput) method works") {
    val expected = gql"""
    input BusinessReferenceInput {
      username: String
      email: String!
      password: String
      branches: [BranchReferenceInput]
      mainBranch: BranchReferenceInput
      businessType: BusinessType
    }
    
    input BranchReferenceInput {
      address: String!
      business: BusinessReferenceInput
    }
    """.renderPretty

    val generatedTypes =
      Document(generator.inputTypes(ReferenceInput).toVector).renderPretty
    assert(generatedTypes == expected)
  }

  test("notificationTypes method works") {
    val expected = gql"""
    type BusinessNotification {
      event: MultiRecordEvent!
      business: Business!
    }
    
    type BranchNotification {
      event: MultiRecordEvent!
      branch: Branch!
    }
    """.renderPretty

    val generatedTypes =
      Document(generator.notificationTypes.toVector).renderPretty
    assert(generatedTypes == expected)
  }
}
