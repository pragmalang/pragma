# Data Validation and Transformation

You can validate data using the functions you pass to directives such as `onWrite` and `onRead`. For example:

```pragma
import "./validators.js" as validators

@onWrite(validators.validateBook)
@1 model Book {
  @1 title: String
  @2 authors: [String]
}
```

where `validateBook` is a JavaScript function in `validators.js`, defined as:

```js
const validateBook = book => {
  if(book.authors.length === 0) {
    throw new Error("A book must have at least one author")
  }
  return book
}
```

This function returns a new version of the input book that is then saved to the database. Notice how this function throws an error if the input book's `authors` array is empty. When the error is thrown, the request fails, and the thrown error's message is returned to the user. Similarly, you can transform the data going out of the database using the functions you pass to `onRead`. For example:

```pragma
import "./transformers.js" as transformers

@onRead(transformers.transformBook)
@1 model Book {
  @2 title: String
  @3 authors: [String]
}
```

where `transformBook` is a JavaScript function defined in `transformers.js` as:

```js
const transformBook = book => ({ ...book, title: book.title.toUpperCase() })
```

The result of the transformation is then returned to the user, or passed to subsequent read hooks if specified by adding more `onRead` directives to the model. The same composition mechanism applies to other types of directives that take function arguments, i.e. `onWrite`, `onLogin`, and `onDelete`.