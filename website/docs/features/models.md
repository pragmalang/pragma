---
id: models
title: Models
---

## What is a model?

A *model* is a definition of a data shape that reflects how the data is stored in the database. Models are similar to GraphQL types, but unlike GraphQL types, Pragma models are defined using the `model` keyword, all model fields are required by default, and [no nullable arrays](./primitive-types.md#array).

Additionally, models and model fields must have unique positive integer *indexes*, which are used to make database schema migrations fully automatic. See [this important note on indexes](#an-important-note-on-indexes).

For example:

```pragma
@user
@1 model User {
  @1 username: String @publicCredential @primary
  @2 password: String @secretCredential
}
```

Here we define a model named `User` that is annotated with the [`@user` directive](./directives.md#user-model-level), and index `1`.

The `User` model has two fields:
* `username`, which is given the type: `String`, and annotated with the [`@publicCredential` directive](./directives.md#publiccredential) and [`@primary`](./directives.md#primary) directives. Note that every model must have exactly one field annotated with `@primary`.
* `password`, which is also given the type: `String`, and annotated with the [`@secretCredential` directive](./directives.md#secretcredential).

## An important note on indexes

:::caution
Fields and models are identified by their indexes (`@1`, `@2`, etc) and names are for generating a human readable GraphQL API.

In other words, if you changed the index of a field or a model, you essentially deleted it and created a new one with the same name, type, and other settings.

We will have warning and error configurations in the future to detect such mistakes before applying the migration. 
:::

## Relations

Relations between models are encoded as field types. For example:

```pragma
@user
@1 model User {
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