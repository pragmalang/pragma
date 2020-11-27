---
id: data-validation-and-transformation
title: Data Validation and Transformation
---

## Validation

Just like with authorization rules, we can validate data using functions. For example:

```pragma
import "./validators.js" as validators { runtime = "nodejs:14" }

@onWrite(validators.validateBook)
@1 model Book {
  @1 id: String @uuid @primary
  @2 title: String
  @2 authors: [String]
}
```

where `validateBook` is a JavaScript function in `validators.js`, defined as:

```js
const validateBook = ({ book }) => {
  if(book.authors.length < 1) {
    throw new Error("A book must have at least one author")
  }
  return { book }
}
```

## Transformation

Let's say that we want every book's title to be in uppercase automatically on every read, we can pass a function to `@onRead` directive on the `Book` model

```pragma
import "./transformers.js" as transformers { runtime = "nodejs:14" }

@onRead(transformers.transformBook)
@1 model Book {
  @1 id: String @uuid @primary
  @2 title: String
  @2 authors: [String]
}
```

`transformBook` is a JavaScript function defined in `transformers.js` as:

```js
const transformBook = ({ book }) => ({ ...book, title: book.title.toUpperCase() })
```

The result of the transformation is then returned to the user, or passed to the next `@onRead`. The same composition mechanism applies to other types of directives that take function arguments, i.e. `onWrite`, `onLogin`, and `onDelete`.