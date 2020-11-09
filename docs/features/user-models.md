# User Models

A user model is a regular model, with the only difference being that it represents a real-world user role in the application. User models have the following features:

## Authentication

User models have *credential* fields that are used for authentication. A field marked as [`@publicCredential`](./directives.md#publiccredential) can be an email, or a username. Public credentials can be used to identify a user publicly among other users. A field marked with [`@secretCredential`](./directives.md#secretcredential) on the other hand, should not be shared with other users, and it is used only in the authentication process, along with a public credential.

> Note: Pragma encrypts secret credentials using the *application secret* before storing them in the database.

## Access Control

You can define *roles* for user models to specify what each kind of user is allowed to perform. See [Permissions](./permissions.md) for more details.

To create a user model, simply annotate a model with the [`@user` directive](./directives.md#user). For example:

```pragma
@user @1 
model User {
  @1 username: String @publicCredential
  @2 password: String @secretCredential
}
```

This tells Pragma to setup authentication flows for the `User` user model, where the `username` and `password` are the user's credentials.

> Note: You can mark multiple fields with the `@publicCredential` directive. However, there can only be one `@secretCredential` field on a model. This allows for functionality such as allowing users to either log in using their email, or their username. For example:

```pragma
@user @1
model User {
  @1 username: String @publicCredential
  @2 email: String @publicCredential
  @3 password: String @secretCredential
}
```

See the [Generated API](../api/index.md) section for more details on how to use `login` queries.