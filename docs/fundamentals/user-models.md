# User Models

A user model is a regular model, with the only difference being that it represents a real-world user role in the application.

To create a user model, annotate the model with the [`@user` directive](./directives.md#user). For example:

```heavenly-x
@user
model User {
    username: String
    password: String
}
```

To setup authentication for a user model, use the [`@publicCredential`](./directives.html#publiccredential-field-level) and [`secretCredential`](./directives.html#secretcredential-field-level) directives:

```heavenly-x
@user
model User {
    username: String @publicCredential
    password: String @secretCredential
}
```

This tells Heavenly-x to setup authentication flows for the `User` user model where the `username` and `password` are the user's credentials.

> Note: You can mark multiple fields with the `@publicCredential` directive. For example:

```heavenly-x
@user
@user
model User {
    username: String @publicCredential
    email: String @publicCredential
    password: String @secretCredential
}
```

> On the other hand you can only mark one field with the `@secretCredtial` directive
