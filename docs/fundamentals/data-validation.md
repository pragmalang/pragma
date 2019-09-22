# Data Validation

The [`@validate`](./directives.html#validate-model-level) directive can be used to define rules that validate incoming data. For example:

```heavenly-x
import validateBook from "./validators.js"

@validate(validators.validateBook)
model Book {
    title: String
    authors: [String]
}
```

The `validateBook` validator is just a JavaScript function in `validators.js`:

```js
const validateBook = ({ self }) => {
    if(!(self.authors.length > 0))
        throw new Error("A book must have at least one author")
}
```

> You can't annotate a field with `@validate`. If you need to validate a field you can use [`@set`](./directives.md#set-field-level) or [`@get`](./directives.md#get-field-level)