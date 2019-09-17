# Introduction

Heavenly-x is a language for building server-side applications by defining data schemas and their associated validation, transformation, and authorization logic. For example:

```heavenly-x
@user
@validate(validator: (user, _) => user.name.length() < 30)
model User {
    @publicCredential
    username: String
    password: String @secretCredential

    firstName: String
    lastName: String

    @transform((_, self, ctx) =>
        self.firstName + self.lastName)
    fullName: String
}
```

The output of the Heavenly compiler is a secure, scalable, idiomatic and easy-to-use GraphQL API that you can run locally, or on the cloud.