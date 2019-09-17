# Data Validation

The [`@validate`](./directives.html#validate-model-level) directive can be used to define rules that validate incoming data. For example:

```heavenly-x
@validate(
    validator:
        (self, context) => self.authors.length() > 0, 
    errorMessage:
        (self, context) => "Book should have one or more authors."
)
model Book {
    title: String
    authors: [String]
}
```

If a model is annotated with multiple `@validate` directives, the validation functions will be executed in sequence, and the validation would succeed only if all validation functions succeed. Once one `@validate` of them fails, the server would respond with an error containing the error message of the failed `@validate`.