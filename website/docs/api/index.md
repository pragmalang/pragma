---
id: api
title: The Generated API
slug: /api
---

Pragma generates a beautiful GraphQL API (CRUD operations) based on model/enum definitions which includes all CRUD operations (Create, Read, Update, Delete), some additional list aggregations, and other list operations like `pushTo`, `pushManyTo`, `removeFrom`, and `removeManyFrom`.

:::note
Subscriptions are not supported yet, but they are very high on the priority list.
:::

## Example

Let's say we have the following `Pragmafile`

### Pragmafile

```pragma title="Pragmafile"
config { projectName = "mini_twitter" }

@user
@1 model User {
  @1 username: String @publicCredential @primary
  @2 password: String @secretCredential
  @3 name: String
  @4 avatarUrl: String
  @5 coverUrl: String?
  @6 bio: String?
  @7 isVerified: Boolean?
  @8 followers: [User]
  @9 following: [User]
  @10 tweets: [Tweet]
  @11 retweets: [Tweet]
  @12 likes: [Tweet]
}

@2 model Tweet {
  @1 id: String @uuid @primary
  @2 user: User
  @3 content: String
  @4 likes: [User]
  @5 retweets: [User]
  @6 replies: [Tweet]
}

# Role definitions and permissions are omitted for demonstration purposes
```

Now if we run `pragma dev` and view/download the schema from the Playground, we can see the [generated schema below](#generated-graphql-schema).

If you think that the generated schema is long, don't worry, you don't have to read all of it. You should always start from the `Query` and the `Mutation` type where each of them contains the available queries and mutations of each **model** respectively which are namespaced by the model's name.

Let's say that you want to get all users (assuming you have the right [permissions](../features/permissions.md) to do so), you start by looking at `Query` then you look at the **GraphQL field named `User`** (`Query.User`), and then to know which operations are available on the **`User` model** you can look into **`UserQueries` type**, then you find `read` and `list`, then ou choose `list` because it allows you to get an array users `[User!]!`.

You can play with the GraphQL API in the Playground after running `pragma dev`.

### Generated GraphQL Schema

```graphql title="schema.graphql"
type Query {
  User: UserQueries
  Tweet: TweetQueries
}

type Mutation {
  User: UserMutations
  Tweet: TweetMutations
}

input ArrayAggInput {
  filter: [ArrayFilter!]
  orderBy: OrderByInput
  from: Int
  to: Int
}

input ArrayFilter {
  predicate: ArrayPredicate!
  and: [ArrayFilter!]
  or: [ArrayFilter!]
  negated: Boolean
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

input TweetInput {
  id: String
  user: UserInput
  content: String
  likes: [UserInput!]
  retweets: [UserInput!]
  replies: [TweetInput!]
}

type TweetMutations {
  create(tweet: TweetInput!): Tweet!
  update(id: String!, tweet: TweetInput!): Tweet!
  delete(id: String!): Tweet!
  createMany(items: [TweetInput!]!): [Tweet!]!
  updateMany(items: [TweetInput!]!): [Tweet!]!
  deleteMany(items: [String!]!): [Tweet!]!
  pushToLikes(id: String!, item: UserInput!): User!
  pushManyToLikes(id: String!, items: [UserInput!]!): [User!]!
  removeFromLikes(id: String!, item: String!): User!
  removeManyFromLikes(id: String!, filter: UserFilter!): [User!]!
  pushToRetweets(id: String!, item: UserInput!): User!
  pushManyToRetweets(id: String!, items: [UserInput!]!): [User!]!
  removeFromRetweets(id: String!, item: String!): User!
  removeManyFromRetweets(id: String!, filter: UserFilter!): [User!]!
  pushToReplies(id: String!, item: TweetInput!): Tweet!
  pushManyToReplies(id: String!, items: [TweetInput!]!): [Tweet!]!
  removeFromReplies(id: String!, item: String!): Tweet!
  removeManyFromReplies(id: String!, filter: TweetFilter!): [Tweet!]!
}

input UserInput {
  username: String
  password: String
  name: String
  avatarUrl: String
  coverUrl: String
  bio: String
  isVerified: Boolean
  followers: [UserInput!]
  following: [UserInput!]
  tweets: [TweetInput!]
  retweets: [TweetInput!]
  likes: [TweetInput!]
}

type UserMutations {
  create(user: UserInput!): User!
  update(username: String!, user: UserInput!): User!
  delete(username: String!): User!
  createMany(items: [UserInput!]!): [User!]!
  updateMany(items: [UserInput!]!): [User!]!
  deleteMany(items: [String!]!): [User!]!
  pushToFollowers(username: String!, item: UserInput!): User!
  pushManyToFollowers(username: String!, items: [UserInput!]!): [User!]!
  removeFromFollowers(username: String!, item: String!): User!
  removeManyFromFollowers(username: String!, filter: UserFilter!): [User!]!
  pushToFollowing(username: String!, item: UserInput!): User!
  pushManyToFollowing(username: String!, items: [UserInput!]!): [User!]!
  removeFromFollowing(username: String!, item: String!): User!
  removeManyFromFollowing(username: String!, filter: UserFilter!): [User!]!
  pushToTweets(username: String!, item: TweetInput!): Tweet!
  pushManyToTweets(username: String!, items: [TweetInput!]!): [Tweet!]!
  removeFromTweets(username: String!, item: String!): Tweet!
  removeManyFromTweets(username: String!, filter: TweetFilter!): [Tweet!]!
  pushToRetweets(username: String!, item: TweetInput!): Tweet!
  pushManyToRetweets(username: String!, items: [TweetInput!]!): [Tweet!]!
  removeFromRetweets(username: String!, item: String!): Tweet!
  removeManyFromRetweets(username: String!, filter: TweetFilter!): [Tweet!]!
  pushToLikes(username: String!, item: TweetInput!): Tweet!
  pushManyToLikes(username: String!, items: [TweetInput!]!): [Tweet!]!
  removeFromLikes(username: String!, item: String!): Tweet!
  removeManyFromLikes(username: String!, filter: TweetFilter!): [Tweet!]!
  loginByUsername(username: String!, password: String!): String!
}

input ArrayPredicate {
  length: IntPredicate
}

input BooleanPredicate {
  eq: Boolean
}

input IntPredicate {
  lt: Int
  gt: Int
  eq: Int
  gte: Int
  lte: Int
}

input OrderByInput {
  field: String!
  order: OrderEnum
}

enum OrderEnum {
  DESC
  ASC
}

input StringPredicate {
  length: IntPredicate
  startsWith: String
  endsWith: String
  pattern: String
  eq: String
}

type Tweet {
  id: String!
  user: User!
  content: String!
  likes(aggregation: UserAggInput): [User!]!
  retweets(aggregation: UserAggInput): [User!]!
  replies(aggregation: TweetAggInput): [Tweet!]!
}

input TweetAggInput {
  filter: [TweetFilter!]
  from: Int
  to: Int
  orderBy: OrderByInput
}

input TweetFilter {
  predicate: TweetPredicate!
  and: [TweetFilter!]
  or: [TweetFilter!]
  negated: Boolean
}

input TweetPredicate {
  id: StringPredicate
  user: UserPredicate
  content: StringPredicate
  likes: ArrayPredicate
  retweets: ArrayPredicate
  replies: ArrayPredicate
}

type TweetQueries {
  read(id: String!): Tweet
  list(aggregation: TweetAggInput): [Tweet!]!
}

type User {
  username: String!
  name: String!
  avatarUrl: String!
  coverUrl: String
  bio: String
  isVerified: Boolean
  followers(aggregation: UserAggInput): [User!]!
  following(aggregation: UserAggInput): [User!]!
  tweets(aggregation: TweetAggInput): [Tweet!]!
  retweets(aggregation: TweetAggInput): [Tweet!]!
  likes(aggregation: TweetAggInput): [Tweet!]!
}

input UserAggInput {
  filter: [UserFilter!]
  from: Int
  to: Int
  orderBy: OrderByInput
}

input UserFilter {
  predicate: UserPredicate!
  and: [UserFilter!]
  or: [UserFilter!]
  negated: Boolean
}

input UserPredicate {
  username: StringPredicate
  name: StringPredicate
  avatarUrl: StringPredicate
  coverUrl: StringPredicate
  bio: StringPredicate
  isVerified: BooleanPredicate
  followers: ArrayPredicate
  following: ArrayPredicate
  tweets: ArrayPredicate
  retweets: ArrayPredicate
  likes: ArrayPredicate
}

type UserQueries {
  read(username: String!): User
  list(aggregation: UserAggInput): [User!]!
}
```