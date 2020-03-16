# Data Validation and Transformation

You can validate data using hooks. Example:

```pragma
import validateBook from "./validators.js"

@onWrite(validators.validateBook)
model Book {
  title: String
  authors: [String]
}
```

The `validateBook` hook is just a JavaScript function in `validators.js`:

```js
export const validateBook = book => {
  if(book.authors.length === 0) {
    throw new Error("A book must have at least one author")
  }
  return book
}
```

You can also transform data using hooks. Example:

```pragma
import transformBook from "./transformers.js"

@onWrite(validators.validateBook)
model Book {
  title: String
  authors: [String]
}
```

The `transformBook` hook is just a JavaScript function in `transformers.js`

```js
export const transformBook = book => ({ ...book, title: book.title.toUpperCase() })
```