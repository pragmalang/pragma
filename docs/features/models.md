# Models

A *model* is a definition of a data shape that reflects how the data is stored in the database. Models are similar to GraphQL types, although Pragma models are defined using the `model` keyword, and all model fields are required by default.

For example:

```pragma
@user
model User {
  username: String @publicCredential
  password: String @secretCredential
}
```

Here we define a model named `User` that is annotated with the [`@user` directive](./directives.md#user-model-level). See [Directives](./directives.md) for more details.

The `User` model has two fields:
* `username`, which is given the type: `String`, and annotated with the [`@publicCredential` directive](./directives.md#publiccredential).
* `password`, which is also given the type: `String`, and annotated with the [`@secretCredential` directive](./directives.md#secretcredential).

Relations between models are encoded as field types. For example:

```pragma
@user
model User {
  username: String @publicCredential
  password: String @secretCredential
  todos: [Todo]
}

model Todo {
  title: String
  content: String
}
```

The `todos` field on `User` has the type `[Todo]` (array of `Todo`s.) This translates to: *"a `User` has many `Todo`s"*.

You can also use the [`@connect` directive](./directives.md#connect) to create many-to-many relationships. For example, if a todo is shared between many `User`s, you can write:

```pragma
@user
model User {
  username: String @publicCredential
  password: String @secretCredential
  todos: [Todo] = [] @connect("USER_TODOS")
}

model Todo {
  title: String
  content: String
  users: [User] = [] @connect("USER_TODOS")
}
```

Now whenever a `Todo` is pushed to a `User`'s array of `todos`, the user will be pushed to the todo's array of users. That is, if there is a todo and a user, and the todo has the user in its `users` array, the user must have the same todo in their `todos` array.
