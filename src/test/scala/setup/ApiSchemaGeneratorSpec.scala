package setup

import setup.schemaGenerator._
import sangria.macros._
import sangria.schema.Schema
import org.scalatest.funsuite.AnyFunSuite

class ApiSchemaGeneratorSpec extends AnyFunSuite {
  val generator = ApiSchemaGenerator(MockSyntaxTree.syntaxTree)

  test("buildApiSchema method works") {
    val gqlDoc = gql"""
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
      
      enum EventEnum {
        REMOVE
        NEW
        CHANGE
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
        field: String
        regex: String!
      }
      
      input ComparisonInput {
        # could be a single field like "friend" or a path "friend.name"
        # If the type of the field or the path is object,
        # then all fields that exist on value of `value: Any!` must be
        # compared with fields with the same name in the model recursively  
        field: String
        value: Any!
      }
      
      scalar Any

      directive @listen(to: EventEnum!) on FIELD
      
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
        create(business: BusinessInput!): Business!
        update(email: String!, business: BusinessInput!): Business!
        delete(email: String!): Business!
        createMany(items: [BusinessInput!]!): [Business]!
        updateMany(items: [BusinessInput!]!): [Business]!
        deleteMany(items: [String!]): [Business]!
        pushToBranches(email: String!, item: BranchInput!): Business!
        pushManyToBranches(email: String!, items: [BranchInput!]!): Business!
        removeFromBranches(email: String!, item: String!): Business!
        removeManyFromBranches(email: String!, filter: FilterInput): Business!
        loginByEmail(email: String!, password: String!): String!
      }
      
      type BranchMutations {
        create(branch: BranchInput!): Branch!
        update(address: String!, branch: BranchInput!): Branch!
        delete(address: String!): Branch!
        createMany(items: [BranchInput!]!): [Branch]!
        updateMany(items: [BranchInput!]!): [Branch]!
        deleteMany(items: [String!]): [Branch]!
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
      }"""

    val expectedSchema = Schema.buildFromAst(gqlDoc)

    val resultSchema =
      Schema.buildFromAst(generator.buildApiSchemaAsDocument)

    val difference = resultSchema.compare(expectedSchema)
    assert(difference.isEmpty)
  }
}
