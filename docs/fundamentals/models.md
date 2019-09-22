# Models

A *model* is a definition of a data shape that reflects how it is stored in the database. For example:

```heavenly-x
@user
model User {
    username: String @publicCredential
    password: String @secretCredential
}
```

Here we define a model named `User` that is annotated with the [`@user` directive](./directives.md#user-model-level). We will discuss [directives](./directives.md) later.

The `User` model has two fields:
* `username`, which is given the type: `String`, and annotated with the [`@publicCredential` directive](./directives.md#publiccredential-field-level).
* `password`, which is also given the type: `String`, and annotated with the [`@secretCredential` directive](./directives.md#secretcredential-field-level).

Check out [2.7](./directives.md) for more information about directives.

Relations between models are encoded as field types. For example:

```heavenly-x
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