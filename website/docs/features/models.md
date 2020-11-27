---
id: models
title: Models
---

A *model* is a definition of a data shape that reflects how the data is stored in the database. Models are similar to GraphQL types, although Pragma models are defined using the `model` keyword, and all model fields are required by default. Additionally, models and model fields must have unique positive integer *indices*, which are used to manage database schema migrations.

For example:

```pragma
@user @1
model User {
  @1 username: String @publicCredential @primary
  @2 password: String @secretCredential
}
```

Here we define a model named `User` that is annotated with the [`@user` directive](./directives.md#user-model-level), and index `1`.

The `User` model has two fields:
* `username`, which is given the type: `String`, and annotated with the [`@publicCredential` directive](./directives.md#publiccredential) and [`@primary`](./directives.md#primary) directives. Note that every model must have exactly one field annotated with `@primary`.
* `password`, which is also given the type: `String`, and annotated with the [`@secretCredential` directive](./directives.md#secretcredential).

Relations between models are encoded as field types. For example:

```pragma
@user @1
model User {
  @1 username: String @publicCredential @primary
  @2 password: String @secretCredential
  @3 todos: [Todo]
}

@2 model Todo {
  @1 title: String @primary
  @2 content: String
}
```

The `todos` field on `User` has the type `[Todo]` (array of `Todo`s.) This translates to: *"a `User` has many `Todo`s"*. Similarly, a `User` can have a reference to a single `Todo`:

```pragma
@user @1
model User {
  @1 username: String @publicCredential @primary
  @2 password: String @secretCredential
  @3 priorityTodo: Todo
}

@2 model Todo {
  @1 title: String @primary
  @2 content: String
}
```