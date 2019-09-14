# Introduction

Heavenly-x is a language for defining data schemas and their associated validation and transformation logic, and authorization rules. For example:

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

The output of the Heavenly compiler is a secure, scalable, idiomatic, and easy to use GraphQL API that you can run locally, or in the cloud.

<script src="../src/syntax-highlighter.js"></script>