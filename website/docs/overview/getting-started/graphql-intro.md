---
id: graphql-intro
title: Never Used GraphQL?
slug: /getting-started/graphql-intro
---

This page is a practical introduction to [GraphQL](https://graphql.org/) that enables you to get started with Pragma.

## What Is GraphQL?

GraphQL is a language specification released by Facebook and standardized in 2015. The language is designed to make retrieving data from front-end applications easier for developers. Some of its advantages are:
* **Type-Safe Communication**: You always know the type/shape of the data you're receiving, and the type of data the server expects from you.
* **Standard Documentation**: All GraphQL APIs must follow a certain standard where it comes to documentation. Many tools know how to interpret this documentation by [*introspecting*](https://spec.graphql.org/June2018/#sec-Introspection) the API.
* **Tooling**: Since GraphQL is a standard, many tools are designed to work with all GraphQL APIs, such as tools that generate TypeScript definitions based on the GraphQL *schema* to be used in your Web app.
* **No Underfetching/Overfetching Of Data**: You select exactly the data that you need, and you can perform multiple operations within a single query.

GraphQL is actually two languages smashed into one: a *schema definition language*, and a *query language*. 

## Schema Definition Language

The schema definition language is what is used to describe the types of data that the API operates on, and what the API is capable of doing with said types.For example:
```graphql
# This is a type definition
type Character {
  name: String! # '!' means "required" or "not nullable"
  friends: [Character] # '[]' mean "array". Types can be recursive
  homeWorld: Planet! # Types can relate to each other
}

# Another type definition
type Planet {
  name: String!
  climate: String!
}

# A special type definition that specifies
# the queries that a client can perform
type Query {
  getCharacter(name: String!): Character
  #            ^^^^^^^^^^^^^   ^^^^^^^^^
  #           Query Arguments  Query Return Type
  # Note that the return type is `Character`, not `Character!`
}
```
:::note
Comments in schema definitions are typically used for documentation. Comments in the example above were used for clarification purposes.
:::

GraphQL APIs have three types of operations that a client can perform:
* Queries: Simple retrieval of data
* Mutations: Changes to existing data, or addition of new data.
* Subscriptions: Retrieval of dta over time.

When developing a GraphQL API, you typically need to specify the queries, mutations, and subscriptions that your server can handle. In the example above, the server is only capable of performing a single query that takes a name of a character, and returns that character. But here's the catch: *you need to specify **how** the query is handled*. Using traditional GraphQL frameworks, you would need to define what are known as *resolvers* to specify how *each field* of the type is fetched from the database. 

## Query Language

Once you're done writing a GraphQL API, you can use one of many GraphQL clients to send operations to your server. Most GraphQL APIs come with a [GraphQL playground](https://github.com/graphql/graphiql/tree/main/packages/graphiql#readme) that you can use to write queries, and click a button to send them to the server, and display the JSON result. But when you're building a graphical application, you would want to use a [client library](https://www.apollographql.com/docs/react/) from your code. An example query would be:
```graphql
query {
  rick:getCharacter(name: "Rick Sanchez") { # We gave the operation a 'rick' alias
    name # Rick's name, just to make sure
    friends {
      name # We only want their names
    }
    homeWorld {
      name # We only want the name
    }
  }
}
```
This query - if Rick exists in the database - would return:
```json
{
  "rick": {
    "name": "Rick Sanchez",
    "friends": [],
    "homeWorld": {
      "name": "Earth"
    }
  }
}
```

:::note
Rick's `friends` array is empty because he has no friends.
:::

## Where Does Pragma Come In?

When you're developing a GraphQL API, you need to specify the schema with all the required operations and types, and then implement resolvers for these operations. Of course, you would need to set up the database first.

What Pragma does is that it only requires that you define a Pragma schema. That's it! It generates a database, GraphQL schema, and all the operations you would need. Plus, with very simple syntax, you can extend the functionality of these operations using [serverless functions](../../features/functions.md) that you `import` as if you were using a normal function from a library. Here's an example Pragma schema:
```pragma
config { projectName = "character_api" }

@1 model Character {
  @1 name: String @primary
  @2 friends: [Character]
  @3 homeWorld: Planet
}

@2 model Planet {
  @1 name: String
  @2 climate: String
}
```
Note the small differences between the Pragma schema and the GraphQL schema. In Pragma, the equivalent of a GraphQL `type` is a `model`. Models and model fields need to have unique *indices* (e.g. `@1`, `@5`), which enables Pragma to perform most database migrations automatically based on changes to the schema. See [Models](../../features/models.md) for more details.

You can explore the GraphQL API generated by Pragma by running the above schema (see [Install Pragma](../install-pragma.md)), or you can check out another example at [The Generated API](../../api/index.md) section.