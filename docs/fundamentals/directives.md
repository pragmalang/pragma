# Directives

A *directive* is an annotation that is used to specify certain behavior of a model/model field.

## Model-level directives
Directives that can mark models

## Field-level directives
Directives that can mark fields

## @user (Model-level)
Used to mark a model as a [user model](./user-models.md).

## @validate (Model-level)
Defines data validation rules for incoming data.
**Args:**
 - `validator`: Function that takes two arguments:
    * `self`: the incoming data object in the case of `CREATE`, or the data object already stored in the database in the case of `UPDATE`.
    * `context`: an object containing data about the request.

 - `errorMessage`: Function that takes two arguments:
    * `self`: the incoming data object in the case of `CREATE`, or the data object already stored in the database in the case of `UPDATE`.
    * `context`: an object containing data about the request.

## @realtime (Model-level/Field-level)
Marks a field or a model as a realtime value

## @publicCredential (Field-level)
Marks a field as a public identifier for it's user model.
> Note: Can only be used on a user model fields

## @secretCredential (Field-level)
Marks a field as a secret credential for it's user model
> Note: Can only be used on a user model fields

## @unique (Field-level)
Marks a field as *unique*, which means that no two instances of the model will have the same value for the field.

## @set (Field-level)
Defines data transformation functions for model fields. This is applied before persisting data to the database (on `UPDATE`s and `CREATE`s).
**Args:**
 - `transformer`: Function that takes an object with three properties:
    * `data`: The incoming data, which is of the same type as the field.
    * `self`: The entire incoming data object (or the existing stored object in the case of `UPDATE`.)
    * `context`: An object containing information about the request (the type of user connection, for example.)

## @get (Field-level)
Defines data transformation functions for model fields. This is applied after querying the data from the database (on `READ`s).
**Args:**
 - `transformer`: Function that takes an object with three properties:
    * `data`: The incoming data, which is of the same type as the field.
    * `self`: The entire incoming data object (or the existing stored object in the case of `UPDATE`.)
    * `context`: An object containing information about the request (the type of user connection, for example.)


## @autoIncrement (Field-level)
Adds auto-increment functionality.
> Notice: Can only be used on `Integer` fields

## @id (Field-level)
Marks a field as an ID for it's model. Marks a field as a primary key if no other field is marked with the [`@primary` directive](#primary-field-level).
> Notice: Can only be used on `String` fields

## @primary (Field-level)
Marks a field as a primary key.
> Notice: Can only be used on non-optional fields with [primitive types](./primitive-types.md)


## @updatedAt (Field-level)
Stores the latest update date of a model on a `Date` field.
> Notice: Can only be used on `Date` fields

## @createdAt (Field-level)
Stores the creation date of a model on a `Date` field.
> Notice: Can only be used on `Date` fields
