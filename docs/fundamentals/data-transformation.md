# Data Transformation

The [`@transform`](./directives.html#transform-field-level) directive is used to transform the values of incoming data before persisting it to the database. For example:

```heavenly-x
model User {
    @publicCredential
    @transform((uname, _, _) => uname.toLowerCase())
    username:  String
}
```

Here we transform the incoming username of a user to be stored in lowercase in the database. The `@transform` directive can only be applied on the field level, and it takes a function of three arguments to use in the data transformation:

* `data`: The incoming data, which is of the same type as the field.
* `self`: The entire incoming data object (or the existing stored object in the case of `UPDATE`.)
* `context`: An object containing information about the request (the type of user connection, for example.)

> Notice that you can use `_` to name unused arguments 

You can apply `@transform` multiple times on a single field:

```heavenly-x
model User {
    @publicCredential
    @transform((uname, _, _) => uname.toLowerCase())
    @transform((uname, _, _) => uname.charAt(0).toUpperCase() + uname.slice(0, uname.length()))
    username:  String
}
```

`@transform` directives are applied in the order in which they were defined. So in this example, the username will be transformed into lowercase, and then the lowercase username is capitalized.