# Directives

A *directive* is an annotation that is used to specify certain behavior for a model/model field. Much like GraphQL directives, Pragma directives start with an `@ ` symbol, followed by the name of the directive, and its arguments (if any).

> *Fun Fact*: "Pragma" is a synonym for "compiler directive", which is where the name of the language (partially) comes from.

The following is a list of all the directives available in Pragma.

## Model-level directives

Directives that can be used to mark models.

### @user
Used to mark a model as a [user model](./user-models.md).

### @onWrite
Registers a function to be called on `CREATE`, `UPDATE`, `MUTATE`, `PUSH_TO`, and `REMOVE_FROM`.

**Arguments:**
 - `function`: A function that takes the incoming data, and returns a the data to be saved to the database.

> *Note*: Functions provided to `onWrite`, `onRead`, `onDelete`, or `onLogin` can fail by throwing an error. This error will be returned to the user with a failure response.

### @onRead
Registers a function to be called on `READ`, and `LIST`.

**Arguments:**
 - `function`: A function that takes the data coming out of the database, and returns the new data.

### @onDelete
Registers a function to be called on `DELETE`

**Arguments:**
 - `function`: A function that takes the data to be deleted, and returns nothing.

### @onLogin
Registers a function to be called when a user logs in.

**Arguments:**
 - `function`: A function that takes the logged in user, and returns nothing.
> *Note*: Can only be used on user models.


## Field-level directives

Directives that can be used to mark model fields.

### @publicCredential
Marks a field as a public identifier for its user model.
> *Note*: Can only be used on user model fields.

### @secretCredential
Marks a field as a secret credential for its user model.
> *Note*: Can only be used on user model fields.

### @unique
Marks a field as *unique*, which means that no two instances of the model will have the same value for the field.

### @autoIncrement
Adds auto-increment functionality.
> *Note*: Can only be used on `Int` fields.

### @primary
Marks a field as a primary key.
> *Note*: Can only be used on non-optional fields with type [`String` or `Int`](./primitive-types.md).

### @connect
Create a many-to-many relationship between two models.
**Arguments:**
 - `name`: `String`, the name of the relationship.

### @updatedAt
Stores the latest update date of a model instance on a `Date` field.
> *Note*: Can only be used on `Date` fields.

### @createdAt
Stores the creation date of a model instance on a `Date` field.
> *Note*: Can only be used on `Date` fields.
