# Introduction

Pragma is a language for building [GraphQL](https://spec.graphql.org/June2018/) APIs by defining data models and their associated validation, transformation, and authorization logic. For example, you can create a `Pragmafile` with the following content:

```pragma
import "./hooks.js" as hooks

@user
@onWrite(function: hooks.validateUser)
@onWrite(function: hooks.setFullName)
@1 model User {
  @1 username: String @publicCredential @priamry
  @2 password: String @secretCredential
  @3 firstName: String
  @4 lastName: String
  @5 fullName: String
  @6 age: Int
}

allow CREATE User
```

With `hooks.js` being a JavaScript file containing two definitions:

```js
const validateUser = user => {
  if(user.age < 18) {
    throw new Error("Age must be over 18")
  }
  return user
}

const setFullName = user => 
  ({...user, fullName: user.firstName + " " + user.lastName})
```

These two functions are used to validate every user object and set its `fullName` field on every `CREATE`, `UPDATE`, or `MUTATE` operation.

Notice the `allow CREATE User` line at the end of the `Pragmafile`. This is a security rule that specifies *anyone* can create a new `User` record. See [Permissions](./features/permissions.md) for more details on access permissions.

Now if you run `pragma dev`, a local server will start, and the GraphQL playground will open in a browser widow. This is all the code you need to set up a GraphQL API with user authentication and very flexible queries, mutations, and subscriptions for creating, reading, updating, and deleting user data. For example:

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
      fullName
    }
  }
}
```

This returns the `username` and `fullName` of the newly created user. See [The Generated API](./api/index.md) section for more details.

For a step-by-step tutorial on Pragma, see the [Getting Started](./getting-started/index.md) chapter.

## Why Pragma?

### It's Declarative

Definitions are concise, readable, and maintainable. [A simple todo app](./getting-started/basic-todo-app.md) with user authentication and permissions can be expressed in under 30 lines of code.

### It Integrates with Many Languages

You can define the functions used for data processing in many languages, including JavaScript, Python, Ruby, R, LLVM languages (Rust, Go, C, C++, etc), and seamlessly compose them using [directives](./features/directives.md).

### It Runs Locally

You can easily install Pragma on you laptop and start development within seconds. Whenever the application is ready for deployment, you can deploy it to your own servers, or any cloud platform that supports Pragma.

### No Vendor Lock-In

Pragma applications are extremely easy to move from any cloud provider to another.

### Databse-agnostic

You can use any kind of database technology that you like with Pragma (Postgres is natively supported). If your favorite database technology is not natively supported by the language, it's very easy to write an adapter and share it with the community.