# Models

A *model* is a definition  of an entity's schema that reflects how it is stored in the database. A model concists of an identifier, a set of data fields, and directives that alter the resulting API. For example:

```heavenly-x
@user
model User {
    username: String @publicCredential
    password: String @secretCredential
}
```

Here we define a model names `User` that is annotated with the `!user` directive. This specifies that this model is of an actual user of the application that should be authenticated.

The `User` model has two fields:
* `username`, which is given the type: `String`, and annotated with `@publicCredential`. This specifies that the `username` field of a `User` is a string of characters and is also used as a public identifier of the `User`.
* `password`, which is also given the type: `String`, and annotated with `@secretCredential`, which specifies that the `password` field should be used along with a public credential to authenticate any `User`.

Relations between models are encoded as fields with model types. For example:

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

The `todos` fieldof `User` has the type `[Todo]` (array of `Todo`s.) This translates to: *"a `User` has many `Todo`s"*.