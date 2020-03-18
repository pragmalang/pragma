# The Generated GraphQL API

## Example:

```pragma
@user
model User {
  username: String @publicCredential @primary
  password: String @secretCredential
  name: String
  avatarUrl: String
  coverUrl: String?
  bio: String?
  isVerified: Boolean?
  followers: [User]?
  following: [User]?
  tweets: [Tweet]?
  retweets: [Tweet]? @connect("RETWEETS")
  likes: [Tweet]? @connect("LIKES")
}

model Tweet {
  user: User
  content: String
  likes: [User] @connect("LIKES")
  retweets: [User] @connect("RETWEETS")
  replies: [Tweet]
}
```
The above Pragma schema will generate the bellow GraphQL API schema:
```graphql
type Query {
  User: UserQueries
	Tweet: TweetQueries
}

type Mutation {
	User: UserMutations
	Tweet: TweetMutations
}

type Subscription {
	User: UserSubscriptions
	Tweet: TweetSubscriptions
}

type User {
  username: String!
  name: String!
  avatarUrl: String!
  coverUrl: String
  bio: String
  isVerified: Boolean
  followers(where: WhereInput): [User]
  following(where: WhereInput): [User]
  tweets(where: WhereInput): [Tweet]
  retweets(where: WhereInput): [Tweet]
  likes(where: WhereInput): [Tweet]
}

type UserQueries {
	read(username: String!): User
	list(where: WhereInput): [User]!
}

type UserSubscriptions {
	read(username: String!): User
	list(where: WhereInput): User
}

type UserMutations {
	login(publicCredential: String, secretCredential: String): String
	create(user: UserInput!): User
	update(username: String!, user: UserInput!): User
	upsert(user: UserInput!): User
	delete(username: String!): User
	createMany(user: [UserInput]!): [User]
	updateMany(user: [UserInput]!): [User]
	upsertMany(user: [UserInput]!): [User]
	deleteMany(items: [String]): [User]

	pushToFollowers(item: UserInput!): User
	pushManyToFollowers(items: [UserInput!]!): [User]
	removeFromFollowers(username: String!): User
	removeManyFromFollowers(items: [String!]!): [User]

	pushToFollowing(item: UserInput!): User
	pushManyToFollowing(items: [UserInput!]!): [User]
	removeFromFollowing(username: String!): User
	removeManyFromFollowing(items: [String!]!): [User]

	pushToTweets(item: TweetInput!): Tweet
	pushManyToTweets(items: [TweetInput!]!): [Tweet]
	removeFromTweets(id: String!): Tweet
	removeManyFromTweets(items: [String!]!): [Tweet]

	pushToRetweets(item: TweetInput!): Tweet
	pushManyToRetweets(items: [TweetInput!]!): [Tweet]
	removeFromRetweets(id: String!): Tweet
	removeManyFromRetweets(items: [String!]!): [Tweet]

	pushToLikes(item: TweetInput!): Tweet
	pushManyToLikes(items: [TweetInput!]!): [Tweet]
	removeFromLikes(id: String!): Tweet
	removeManyFromLikes(items: [String!]!): [Tweet]

	recover(id: String!): User
	recoverMany(id: [String]): [User]
}

input UserInput {
  username: String
  password: String
  name: String
  avatarUrl: String
  coverUrl: String
  bio: String
  isVerified: Boolean
  followers: [UserInput]
  following: [UserInput]
  tweets: [TweetInput]
  retweets: [TweetInput]
  likes: [TweetInput]
}

type Tweet {
  user: User
  content: String
  likes(where: WhereInput): [User]
  retweets(where: WhereInput): [User]
  replies(where: WhereInput): [Tweet]
}

type TweetQueries {
	read(id: String!): Tweet
	list(where: WhereInput): [Tweet]!
}

type TweetSubscriptions {
	read(id: String!): Tweet
	list(where: WhereInput): Tweet
}

type TweetMutations {
	create(tweet: TweetInput!): Tweet
	update(id: String!, tweet: TweetInput!): Tweet
	upsert(tweet: TweetInput!): Tweet
	delete(id: String!): Tweet
	createMany(tweet: [TweetInput]!): [Tweet]
	updateMany(tweet: [TweetInput]!): [Tweet]
	upsertMany(tweet: [TweetInput]!): [Tweet]
	deleteMany(items: [String!]!): [Tweet]
	recover(id: String!): Tweet
	recoverMany(id: [String]): [Tweet]

	pushToLikes(item: UserInput!): User
	pushManyToLikes(items: [UserInput!]!): [User]
	removeFromLikes(username: String!): User
	removeManyFromLikes(items: [String!]!): [User]

	pushToRetweets(item: UserInput!): User
	pushManyToRetweets(items: [UserInput!]!): [User]
	removeFromRetweets(username: String!): User
	removeManyFromRetweets(items: [String!]!): [User]

	pushToReplies(item: TweetInput!): Tweet
	pushManyToReplies(items: [TweetInput!]!): [Tweet]
	removeFromReplies(id: String!): Tweet
	removeManyFromReplies(items: [String!]!): [Tweet]
}

input TweetInput {
  user: UserInput
  content: String
  likes: [UserInput]
  retweets: [UserInput]
  replies: [TweetInput]
}
```

With the following built-in GraphQL types:

```graphql
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
directive @listen(to: EVENT_ENUM!) on FIELD # on field selections inside a subscription
```