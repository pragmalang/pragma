# Data Validation

The `@validate` directive can be used to annotate a model to define rules that validate incoming data. For example:

```heavenly-x
@validate(
    validator: (b, _, _) => b.authors.length() > 0, 
    errorMessage: (_) => "Book should have one or more authors.")
model Book {
    title: String
    authors: [String]
}
```

The `validator` argument of `@validate` is a function that takes three arguments:
* `data`: the incoming data object.
* `self`: the incoming data objec in the case of `CREATE`, or the data object already stored in the database in the case of `UPDATE`.
* `context`: an object containing data about the request.

In this example, we gave the name `b` to the `data` argument of the validation function, and we specify that the book's array of authors should contain at least one author. If the validation function returns `false`, the application will respond with an error message.

The `errorMessage` argument of `@validate` is a function that takes as an arguments the incoming data object, and the stored object (the same object as the incoming data object in the case of `CREATE`.) The function should return a `String` containing the message that will be returned in the response.

If `@validate` is applied multiple times on a model, the validation functions will be executed in sequence, and the validation would succeed only if all validation functions succeed.