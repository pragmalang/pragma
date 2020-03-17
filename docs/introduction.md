# Introduction

Pragma is a language for building GraphQL APIs by defining data models and their associated validation, transformation, and authorization logic. For example, you can create a `Pragmafile` with the following contents:

```pragma
import hooks from "./hooks.js"

@user
@onWrite(hooks.validateUser)
@onWrite(hooks.setFullName)
model User {
  username: String @publicCredential @priamry
  password: String @secretCredential
  firstName: String
  lastName: String
  fullName: String
  age: Int
}

allow CREATE User
```

`hooks.js` is a JavaScript file containing two definitions:

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

These two functions are used to validate every user object and set its `fullName` field on every `CREATE`, `UPDATE`, or `MUTATE` event. See Events for more details on events.

Notice the `allow CREATE User` rule at the end of the `Pragmafile`. This rule specifies that *anyone* can create a new `User` account. See Permissions for more details on access permissions.

Now if you run `pragma dev`, a local server will start, and the GraphQL playground will open in a browser widow. This is all the code you need to set up a GraphQL API with user authentication and very flexible queries/mutations/subscriptions for creating, reading, updating, and deleting user data. For example:

```graphql
mutation createUser {
  User {
    create(data: {
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

This returns the `username` and `fullName` of the newly created user. See The [Generated API](./api/index.md) section for more details.