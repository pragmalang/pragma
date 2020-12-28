---
id: directives
title: Directives
---

A *directive* is an annotation that is used to specify certain behavior for a model/model field. Much like GraphQL directives, Pragma directives start with an `@` symbol, followed by the name of the directive, and its arguments (if any) which are specified by name.

:::note Fun Fact 
"Pragma" is a synonym for "compiler directive", which is where the name of the language (partly) comes from.
:::

The following is a list of all the directives available in Pragma:
## Model-level directives

Directives that can be used to mark models.

### @user
Used to mark a model as a [user model](./user-models.md).

### @onWrite
Registers a function to be called on `CREATE`, `UPDATE`, `MUTATE`, `PUSH_TO`, and `REMOVE_FROM`.

**Arguments:**
 - `function`: A function that takes the incoming data, and returns the data to be saved to the database.

:::note
Functions passed to `onWrite`, `onRead`, `onDelete`, or `onLogin` can fail by throwing an error. This error will be returned to the user with a failure response.
:::

### @onRead
Registers a function to be called on `READ`, and `LIST` operation results.

**Arguments:**
 - `function`: A function that takes the data coming out of the database, and returns the new data.

### @onDelete
Registers a function to be called on `DELETE`

**Arguments:**
 - `function`: A function that takes the data to be deleted, and returns nothing.

### @onLogin
Registers a function to be called when a user logs in.

:::note
Can only be used on user models.
:::

**Arguments:**
 - `function`: A function that takes the logged in user, and returns nothing.


## Field-level directives

Directives that can be used to mark model fields.

### @publicCredential
Marks a field as a public identifier for its user model.
:::note
Can only be used on user model fields.
:::

### @secretCredential
Marks a field as a secret credential for its user model.
:::note
Can only be used on user model fields.
:::

### @unique
Marks a field as being unique for each record.

### @autoIncrement
Adds auto-increment functionality.
:::note
Can only be used on `Int` fields.
:::

### @primary
Marks a field as a primary key.
:::note
Can only be used on non-optional fields with type [`String` or `Int`](./primitive-types.md).
:::
