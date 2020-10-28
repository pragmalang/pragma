# Introduction

## What is Pragma?

Pragma is a language for building [GraphQL](https://graphql.org/) APIs in no time, by defining data models and their associated validation, transformation, and authorization logic. 

For example, you can create a `Pragmafile` with the following content:

```pragma
config { projectName = "my_first_app" }

import "./hooks.js" as hooks { runtime = "nodejs:14" }

@user
@onWrite(function: hooks.validateUser)
@onWrite(function: hooks.setFullName)
@1 model User {
  @1 username: String @publicCredential @primary
  @2 password: String @secretCredential
  @3 firstName: String
  @4 lastName: String
  @5 fullName: String
  @6 age: Int
}

allow CREATE User
allow READ_ON_CREATE User.username

role User {
  allow [READ, UPDATE] self
  allow READ User
}
```

With `hooks.js` being a JavaScript file containing two definitions:

```js
const validateUser = user => {
  if (user.age < 18) {
    throw new Error("Age must be over 18")
  }
  return user
}

const setFullName = user =>
  ({ ...user, fullName: user.firstName + " " + user.lastName })

module.exports = { validateUser, setFullName }
```

These two functions are used to validate every user object and set its `fullName` field on every `CREATE`, `UPDATE`, and `MUTATE` operation.

Pragma has built-in support for *authorization*, meaning you can define [*access rules*](./features/permissions.md) to specify the actions each kind of user can perform, and *when*.

This is all the code you need to set up a GraphQL API with user authentication and very flexible queries and mutations for creating, reading, updating, and deleting user data. For example:

```graphql
mutation createUser {
  User {
    create(user: {
      username: "johndoe",
      password: "password1234",
      firstName: "John",
      lastName: "Doe",
      age: 21
    }) {
      username
    }
  }
}
```

This returns the `username` and `fullName` of the newly created user. See [The Generated API](./api/index.md) section for more details.

For a step-by-step tutorial on Pragma, see the [Getting Started section](./getting-started/index.md).

## Why Pragma?

### It Saves Your Time

Pragma doesn't make you worry about networking, writing resolvers, or (when using the Pragma Cloud) deployment. It offers what we believe is the best server-side application development experience.

### It's Declarative

Definitions are concise, readable, high-level, and maintainable. It keeps configuration and boilerplate you need to write and keep in mind at a minimum (no code generation, all the complexity is abstracted). You focus on your business logic and nothing else.

[A simple todo app](./getting-started/index.md#basic-todo-app) with user authentication and permissions can be expressed in under 30 lines of code.

### It Integrates with Many Languages

You can define the functions used for data processing in many languages, including  JavaScript (NodeJS), Go, Java, Scala, PHP, Python, Ruby, Swift, Ballerina, .NET and Rust, and seamlessly compose them using [directives](./features/directives.md). This is due to Pragma being built on top of [Apache Openwhisk](https://openwhisk.apache.org/).

### It Runs Locally

You can easily install Pragma on you laptop and start development within seconds. The only two requirements for running Pragma are Docker and Docker Compose. See the [Getting Started](./getting-started/index.md) chapter.

One your application is ready for deployment, you can deploy it to your own servers, or any cloud platform that supports Pragma.

### No Vendor Lock-In

Pragma applications are extremely easy to move from any cloud provider to another, as long as they have a Kubernetes offering.
