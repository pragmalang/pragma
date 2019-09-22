# Introduction

Heavenly-x is a language for building server-side applications by defining data schemas and their associated validation, transformation, and authorization logic. For example:

```heavenly-x
import "./setters.js" as setters
import "./validators.js" as validators

@user
@validate(validators.validateUser)
model User {
    @publicCredential
    username: String
    password: String @secretCredential

    firstName: String
    lastName: String

    @set(setters.setFullName)
    fullName: String

    age: Integer
}
```

`validateUser` is a JavaScript function in `validators.js`

```js
const validateUser = ({ self }) => self.age >= 18
```

and so does `setFullName` in `setters.js`

```js
const setFullName = ({ self }) => self.firstName + self.lastName
```

The output of the Heavenly-x compiler is a secure, scalable, idiomatic and easy-to-use GraphQL API that you can run locally, or on the cloud.
