package setup

import org.scalatest._
import sangria.renderer.QueryRenderer
import setup.DefualtApiSchemaGenerator._
import sangria.ast.Document
import sangria.macros._
import domain.Implicits._
import scala.util.{Failure, Success}

class DefaultApiSchemaGeneratorSpec extends FunSuite {
  val generator = DefaultApiSchemaGenerator(MockSyntaxTree.syntaxTree)

  test("outputTypes method works") {
    val expected = gql"""
    type Business {
      username: String
      email: String!
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

  test("buitlinGraphQlTypeDefinitions method works") {
    val expected = gql"""
      input EqInput {
        field: String!
        value: Any!
      }
      
      input WhereInput {
        filter: LogicalFilterInput
        orderBy: OrderByInput
        range: RangeInput
        first: Int
        last: Int
        skip: Int
      }
      
      input OrderByInput {
        field: String!
        order: OrderEnum
      }
      
      enum OrderEnum {
        DESC
        ASC
      }
      
      input RangeInput {
        before: ID!
        after: ID!
      }
    
      input LogicalFilterInput {
        AND: [LogicalFilterInput]
        OR: [LogicalFilterInput]
        predicate: FilterInput
      }
      
      input FilterInput {
        eq: EqInput
      }
      
      enum MultiRecordEvent {
        CREATE
        UPDATE
        READ
        DELETE
      }
      
      enum SingleRecordEvent {
        UPDATE
        READ
        DELETE
      }
      
      scalar Any
      """.renderPretty

    val generatedTypes =
      Document(DefualtApiSchemaGenerator.buitlinGraphQlTypeDefinitions.toVector).renderPretty

    assert(generatedTypes == expected)
  }

  test("queryType method works") {
    val expected = gql"""
    type Query {
      business(email: String!): Business!
      branch(address: String!): Branch!
      manyBusinesses(where: WhereInput): [Business]!
      branches(where: WhereInput): [Branch]!
      countManyBusinesses(where: WhereInput): Int!
      countBranches(where: WhereInput): Int!
      businessExists(filter: LogicalFilterInput!): Int!
      branchExists(filter: LogicalFilterInput!): Int!
    }
    """.renderPretty

    val queryType =
      generator.queryType.renderPretty

    assert(queryType == expected)
  }

  test("mutationType method works") {
    val expected = gql"""
    type Mutation {
      loginBusiness(publicCredential: String, secretCredential: String): String!
      createBusiness(business: BusinessObjectInput!): Business!
      createBranch(branch: BranchObjectInput!): Branch!
      updateBusiness(email: String!, business: BusinessOptionalInput!): Business!
      updateBranch(address: String!, branch: BranchOptionalInput!): Branch!
      upsertBusiness(business: BusinessOptionalInput!): Business!
      upsertBranch(branch: BranchOptionalInput!): Branch!
      deleteBusiness(email: String!): Business!
      deleteBranch(address: String!): Branch!
      createManyBusinesses(manyBusinesses: [BusinessObjectInput]!): [Business]!
      createBranches(branches: [BranchObjectInput]!): [Branch]!
      updateManyBusinesses(manyBusinesses: [BusinessReferenceInput]!): [Business]!
      updateBranches(branches: [BranchReferenceInput]!): [Branch]!
      upsertManyBusinesses(manyBusinesses: [BusinessOptionalInput]!): [Business]!
      upsertBranches(branches: [BranchOptionalInput]!): [Branch]!
      deleteManyBusinesses(email: [String]!): [Business]!
      deleteBranches(address: [String]!): [Branch]!
    }
    """.renderPretty

    val mutationType =
      generator.mutationType.renderPretty

    assert(mutationType == expected)
  }

  test("subscriptionType method works") {
    val expected = gql"""
    type Subscription {
      business(email: String, on: [SingleRecordEvent]): BusinessNotification!
      branch(address: String, on: [SingleRecordEvent]): BranchNotification!
      manyBusinesses(where: WhereInput, on: [MultiRecordEvent]): [BusinessNotification]!
      branches(where: WhereInput, on: [MultiRecordEvent]): [BranchNotification]!
    }
    """.renderPretty

    val subscriptionType =
      generator.subscriptionType.renderPretty

    assert(subscriptionType == expected)
  }

  test("buildApiSchema method works") {
    val expected = gql"""
    type Query {
      business(email: String!): Business!
      branch(address: String!): Branch!
      manyBusinesses(where: WhereInput): [Business]!
      branches(where: WhereInput): [Branch]!
      countManyBusinesses(where: WhereInput): Int!
      countBranches(where: WhereInput): Int!
      businessExists(filter: LogicalFilterInput!): Int!
      branchExists(filter: LogicalFilterInput!): Int!
    }
    
    type Mutation {
      loginBusiness(publicCredential: String, secretCredential: String): String!
      createBusiness(business: BusinessObjectInput!): Business!
      createBranch(branch: BranchObjectInput!): Branch!
      updateBusiness(email: String!, business: BusinessOptionalInput!): Business!
      updateBranch(address: String!, branch: BranchOptionalInput!): Branch!
      upsertBusiness(business: BusinessOptionalInput!): Business!
      upsertBranch(branch: BranchOptionalInput!): Branch!
      deleteBusiness(email: String!): Business!
      deleteBranch(address: String!): Branch!
      createManyBusinesses(manyBusinesses: [BusinessObjectInput]!): [Business]!
      createBranches(branches: [BranchObjectInput]!): [Branch]!
      updateManyBusinesses(manyBusinesses: [BusinessReferenceInput]!): [Business]!
      updateBranches(branches: [BranchReferenceInput]!): [Branch]!
      upsertManyBusinesses(manyBusinesses: [BusinessOptionalInput]!): [Business]!
      upsertBranches(branches: [BranchOptionalInput]!): [Branch]!
      deleteManyBusinesses(email: [String]!): [Business]!
      deleteBranches(address: [String]!): [Branch]!
    }
    
    type Subscription {
      business(email: String, on: [SingleRecordEvent]): BusinessNotification!
      branch(address: String, on: [SingleRecordEvent]): BranchNotification!
      manyBusinesses(where: WhereInput, on: [MultiRecordEvent]): [BusinessNotification]!
      branches(where: WhereInput, on: [MultiRecordEvent]): [BranchNotification]!
    }
    
    input EqInput {
      field: String!
      value: Any!
    }
    
    input WhereInput {
      filter: LogicalFilterInput
      orderBy: OrderByInput
      range: RangeInput
      first: Int
      last: Int
      skip: Int
    }
    
    input OrderByInput {
      field: String!
      order: OrderEnum
    }
    
    enum OrderEnum {
      DESC
      ASC
    }
    
    input RangeInput {
      before: ID!
      after: ID!
    }
    
    input LogicalFilterInput {
      AND: [LogicalFilterInput]
      OR: [LogicalFilterInput]
      predicate: FilterInput
    }
    
    input FilterInput {
      eq: EqInput
    }
    
    enum MultiRecordEvent {
      CREATE
      UPDATE
      READ
      DELETE
    }
    
    enum SingleRecordEvent {
      UPDATE
      READ
      DELETE
    }
    
    scalar Any
    
    type Business {
      username: String
      email: String!
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
    
    type BusinessNotification {
      event: MultiRecordEvent!
      business: Business!
    }
    
    type BranchNotification {
      event: MultiRecordEvent!
      branch: Branch!
    }
    """.renderPretty

    val schema =
      generator.buildApiSchema.renderPretty

    import java.io.PrintWriter
    import scala.language.postfixOps
    val createFile =
      () => new PrintWriter("output.gql") { write(schema); close }
    val deleteFile = () => "rm output.gql" $ "Removing output.gql failed"

    createFile()
    "graphql-inspector similar output.gql" $ "Schema validation failed" match {
      case Failure(exception) => {
        deleteFile()
        throw exception
      }
      case Success(value) => deleteFile()
    }
    deleteFile()

    assert(schema == expected)
  }
}
