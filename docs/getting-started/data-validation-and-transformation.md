# Data Validation and Transformation

You can validate data using the functions you pass to directives such as `onWrite` and `onRead`. For example:

```pragma
import validators from "./validators.js"

@onWrite(validators.validateBook)
@1 model Book {
  @1 title: String
  @2 authors: [String]
}
```

where `validateBook` a JavaScript function in `validators.js`, defined as:

```js
const validateBook = book => {
  if(book.authors.length === 0) {
    throw new Error("A book must have at least one author")
  }
  return book
}
```

Notice how this function throws an error if the input book's `authors` array is empty. When the error is thrown, the request fails, and the thrown error's message is returned.

You can also transform data using the function you pass to `onWrite`. For example:

```pragma
import transformBook from "./transformers.js"

@onWrite(validators.validateBook)
@1 model Book {
  @2 title: String
  @3 authors: [String]
}
```

where `transformBook` is a JavaScript function in `transformers.js` as:

```js
const transformBook = book => ({ ...book, title: book.title.toUpperCase() })
```

This function returns a new version of the input book that is then saved to the database. Similarly, you can transform the data going out of the database using the function you pass to `onRead`.