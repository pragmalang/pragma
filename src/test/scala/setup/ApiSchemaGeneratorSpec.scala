package setup

import org.scalatest._
import setup.schemaGenerator._
import sangria.macros._

class ApiSchemaGeneratorSpec extends FunSuite {
  val generator = ApiSchemaGenerator(MockSyntaxTree.syntaxTree)

  test("buildApiSchema method works") {
    val expected = gql"""
      type Query {
        Business: BusinessQueries
        Branch: BranchQueries
      }
      
      type Mutation {
        Business: BusinessMutations
        Branch: BranchMutations
      }
      
      type Subscription {
        Business: BusinessSubscriptions
        Branch: BranchSubscriptions
      }
      
      input WhereInput {
        filter: FilterInput
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
      
      input FilterInput {
        not: FilterInput
        and: FilterInput
        or: FilterInput
        eq: ComparisonInput # works only when the field is of type String or Int or Float
        gt: ComparisonInput # works only when the field is of type Float or Int
        gte: ComparisonInput # works only when the field is of type Float or Int
        lt: ComparisonInput # works only when the field is of type Float or Int
        lte: ComparisonInput # works only when the field is of type Float or Int
        matches: MatchesInput # works only when the field is of type String
      }
      
      input MatchesInput {
        # could be a single field like "friend" or a path "friend.name"
        # works only when the field is of type String
        field: String!
        regex: String!
      }
      
      input ComparisonInput {
        # could be a single field like "friend" or a path "friend.name"
        # If the type of the field or the path is object,
        # then all fields that exist on value of `value: Any!` must be
        # compared with fields with the same name in the model recursively  
        field: String!
        value: Any!
      }
      
      enum EVENT_ENUM {
        REMOVE
        NEW
        CHANGE
      }
      
      scalar Any
      
      directive @filter(filter: FilterInput!) on FIELD
      
      directive @order(order: OrderEnum!) on FIELD
      
      directive @range(range: RangeInput!) on FIELD
      
      directive @first(first: Int!) on FIELD
      
      directive @last(last: Int!) on FIELD
      
      directive @skip(skip: Int!) on FIELD
      
      directive @listen(to: EVENT_ENUM!) on FIELD
      
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
      
      input BusinessInput {
        username: String
        email: String
        password: String
        branches: [BranchInput]
        mainBranch: BranchInput
        businessType: BusinessType
      }
      
      input BranchInput {
        address: String
        business: BusinessInput
      }
      
      type BusinessMutations {
        login(publicCredential: String!, password: String!): String!
        create(business: BusinessInput!): Business!
        update(email: String!, business: BusinessInput!): Business!
        delete(email: String!): Business!
        recover(email: String!): Business!
        createMany(items: [BusinessInput!]!): [Business]!
        updateMany(items: [BusinessInput!]!): [Business]!
        deleteMany(items: [String!]): [Business]!
        recoverMany(items: [String!]): [Business]!
        pushToBranches(item: BranchInput!): Branch!
        pushManyToBranches(item: [BranchInput!]!): [Branch]!
        removeFromBranches(item: BranchInput!): Branch!
        removeManyFromBranches(item: [BranchInput!]!): [Branch]!
      }
      
      type BranchMutations {
        create(branch: BranchInput!): Branch!
        update(address: String!, branch: BranchInput!): Branch!
        delete(address: String!): Branch!
        recover(address: String!): Branch!
        createMany(items: [BranchInput!]!): [Branch]!
        updateMany(items: [BranchInput!]!): [Branch]!
        deleteMany(items: [String!]): [Branch]!
        recoverMany(items: [String!]): [Branch]!
      }
      
      type BusinessQueries {
        read(email: String!): Business
        list(where: WhereInput): [Business]!
      }
      
      type BranchQueries {
        read(address: String!): Branch
        list(where: WhereInput): [Branch]!
      }
      
      type BusinessSubscriptions {
        read(email: String!): Business
        list(where: WhereInput): Business
      }
      
      type BranchSubscriptions {
        read(address: String!): Branch
        list(where: WhereInput): Branch
      }""".renderPretty

    val schema =
      generator.buildApiSchemaAsDocument.renderPretty

    // import java.io.PrintWriter
    // import scala.language.postfixOps
    // val createFile =
    //   () => new PrintWriter("output.gql") { write(schema); close }
    // val deleteFile = () => "rm output.gql" $ "Removing output.gql failed"

    // createFile()
    // "graphql-inspector similar output.gql" $ "Schema validation failed" match {
    //   case Failure(exception) => {
    //     deleteFile()
    //     throw exception
    //   }
    //   case Success(value) => deleteFile()
    // }
    // deleteFile()

    assert(schema == expected)
  }
}
