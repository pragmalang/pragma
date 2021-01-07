package setup

import setup.schemaGenerator._
import sangria.macros._
import sangria.schema.Schema
import org.scalatest.funsuite.AnyFunSuite

class ApiSchemaGeneratorSpec extends AnyFunSuite {
  val generator = ApiSchemaGenerator(MockSyntaxTree.syntaxTree)

  test("buildApiSchema method works") {
    val gqlDoc = gql"""
    input ArrayPredicate {
      length: IntPredicate
    }

    input BooleanAggInput {
      filter: [BooleanFilter!]
      orderBy: OrderByInput
      from: Int
      to: Int
    }

    input BooleanFilter {
      predicate: BooleanPredicate!
      and: [BooleanFilter!]
      or: [BooleanFilter!]
      negated: Boolean
    }

    input BooleanPredicate {
      eq: Boolean
    }

    type Branch {
      address: String!
      business: Business!
    }

    input BranchAggInput {
      filter: [BranchFilter!]
      from: Int
      to: Int
      orderBy: OrderByInput
    }

    input BranchFilter {
      predicate: BranchPredicate!
      and: [BranchFilter!]
      or: [BranchFilter!]
      negated: Boolean
    }

    input BranchInput {
      address: String
      business: BusinessInput
    }

    type BranchMutations {
      create(branch: BranchInput!): Branch!
      update(address: String!, branch: BranchInput!): Branch!
      delete(address: String!): Branch!
      createMany(items: [BranchInput!]!): [Branch!]!
      updateMany(items: [BranchInput!]!): [Branch!]!
      deleteMany(items: [String!]!): [Branch!]!
    }

    input BranchPredicate {
      address: StringPredicate
      business: BusinessPredicate
    }

    type BranchQueries {
      read(address: String!): Branch
      list(aggregation: BranchAggInput): [Branch!]!
    }

    type Business {
      username: String
      email: String!
      branches(aggregation: BranchAggInput): [Branch!]!
      mainBranch: Branch
      businessType: BusinessType!
    }

    input BusinessAggInput {
      filter: [BusinessFilter!]
      from: Int
      to: Int
      orderBy: OrderByInput
    }

    input BusinessFilter {
      predicate: BusinessPredicate!
      and: [BusinessFilter!]
      or: [BusinessFilter!]
      negated: Boolean
    }

    input BusinessInput {
      username: String
      email: String
      password: String
      branches: [BranchInput!]
      mainBranch: BranchInput
      businessType: BusinessType
    }

    type BusinessMutations {
      create(business: BusinessInput!): Business!
      update(email: String!, business: BusinessInput!): Business!
      delete(email: String!): Business!
      createMany(items: [BusinessInput!]!): [Business!]!
      updateMany(items: [BusinessInput!]!): [Business!]!
      deleteMany(items: [String!]!): [Business!]!
      pushToBranches(email: String!, item: BranchInput!): Branch!
      pushManyToBranches(email: String!, items: [BranchInput!]!): [Branch!]!
      removeFromBranches(email: String!, item: String!): Branch!
      removeManyFromBranches(email: String!, filter: BranchFilter!): [Branch!]!
      loginByEmail(email: String!, password: String!): String!
    }

    input BusinessPredicate {
      username: StringPredicate
      email: StringPredicate
      branches: ArrayPredicate
      mainBranch: BranchPredicate
      businessType: BusinessTypePredicate
    }

    type BusinessQueries {
      read(email: String!): Business
      list(aggregation: BusinessAggInput): [Business!]!
    }

    enum BusinessType {
      FOOD
      CLOTHING
      OTHER
    }

    input BusinessTypeAggInput {
      filter: [BusinessTypeFilter!]
      from: Int
      to: Int
      orderBy: OrderByInput
    }

    input BusinessTypeFilter {
      predicate: BusinessTypePredicate!
      and: [BusinessTypeFilter!]
      or: [BusinessTypeFilter!]
      negated: Boolean
    }

    input BusinessTypePredicate {
      eq: BusinessTypePredicate
    }

    input FloatAggInput {
      filter: [FloatFilter!]
      orderBy: OrderByInput
      from: Int
      to: Int
    }

    input FloatFilter {
      predicate: FloatPredicate!
      and: [FloatFilter!]
      or: [FloatFilter!]
      negated: Boolean
    }

    input FloatPredicate {
      lt: Float
      gt: Float
      eq: Float
      gte: Float
      lte: Float
    }

    input IntAggInput {
      filter: [IntFilter!]
      orderBy: OrderByInput
      from: Int
      to: Int
    }

    input IntFilter {
      predicate: IntPredicate!
      and: [IntFilter!]
      or: [IntFilter!]
      negated: Boolean
    }

    input IntPredicate {
      lt: Int
      gt: Int
      eq: Int
      gte: Int
      lte: Int
    }

    type Mutation {
      Business: BusinessMutations
      Branch: BranchMutations
    }

    input OrderByInput {
      field: String
      order: OrderEnum!
    }

    enum OrderEnum {
      ASCENDING
      DESCENDING
      SHUFFLED
    }

    type Query {
      Business: BusinessQueries
      Branch: BranchQueries
    }

    input StringAggInput {
      filter: [StringFilter!]
      orderBy: OrderByInput
      from: Int
      to: Int
    }

    input StringFilter {
      predicate: StringPredicate!
      and: [StringFilter!]
      or: [StringFilter!]
      negated: Boolean
    }

    input StringPredicate {
      length: IntPredicate
      startsWith: String
      endsWith: String
      pattern: String
      eq: String
    }

    enum EventEnum {
      REMOVE
      NEW
      CHANGE
    }
    """

    val expectedSchema = Schema.buildFromAst(gqlDoc)

    val resultSchema =
      Schema.buildFromAst(generator.build)

    val difference = resultSchema.compare(expectedSchema)
    assert(difference.isEmpty)
  }
}
