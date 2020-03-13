# General Notes
- Uploading images will not be using the GraphQL API, instead, an endpoint for each field of type file will be genrated following this pattern `/upload/{model.id}/{field.id}/<recordId>` where `recordId` is an actual URL paramater provided by the client when uploading a file. Authorization rules will apply to any field of type `File` as any other field and it's considered as an `UPDATE` operation so `@onWrite` hooks will be called.

# Validation Rules

- ✔️ `T` types are mapped to `T!` GraphQL types
- ✔️ `[T]` types are mapped to `[T]!` GraphQL types
- ✔️ `T?` types are mapped to `T` GraphQL types
- ✔️ `[T]?` types are mapped to `[T]` GraphQL types
- The following is list of reserved type names:
  - `Subscription`
  - `Query`
  - `Mutation`
  - `WhereInput`
  - `OrderEnum`
  - `RangeInput`
  - `OrderByInput`
  - `FilterInput`
  - `Any`
  - `ComparisonInput`
  - `MatchesInput`
  - `EVENT_ENUM`
- The following is a list of reserved field names:
  - `_id`
  - `_event`
- If there is no `permit` block then everything is permitted (Authorization always return `true`)
- If there exist `CREATE SomeType.someField` an error must be thrown since you can only update a field but you can't create one
- If a type has a recursive field (field of the same type), this field must be optional
- If there's a circular dependency between two types `A` and `B`, one of the dependant fields must be optional. (Not sure about this rule, check Prisma)
- Fields with `@publicCredential` must ALWAYS be of type `String` or `String?`
- Fields with `@secretCredential` must ALWAYS be of type `String`
- The client should be able to perform mutations with full objects or ID strings (DB references) as input values. This can be solved with a scalar input type
- Fields annotated with `@primary` must only be of type `Integer` or `String`
- Every model must have one and only one field annotated with `@primary`, if not a field called `_id: String @primary @id` is created
- If a plural is specified for a model using the `@plural(name: String)` directive it must not match the name of any other defined model
- Every user is prohibited from accessing any resource by default unless:
  - Their exist an access rule that allows them to access it
- The built-in hook function `ifSelf` can only be applied to access rules where the resource is of the same type as the role of the enclosing role block. A valid example would be
```
acl {
  role Instructor {
    allow ALL Instructor ifSelf
  }
}
```
an invalid example would be
```
acl {
  role Instructor {
    allow ALL Instructor.name ifSelf
  }
}
```
- Every model or enum should have a unique case-insensitive name, meaning, `user` is the same as `User`. This is important because in many parts of Heavenly-x's codebase `id`s in general are treated in a case-insensitive manner because of some design choices. (Rethink this)
- A user model must have one or more fields annotated with `@publicCredential`
- A user model must have one or zero fields annotated with `@secretCredential`
- A field of type `Integer` annotated with `@id` is the same as a field of type `Integer` annotated with `@unique` and `@autoIncrement`
- A field of type `String` annotated with `@id` is the same as a field of type `String` annotated with `@unique` and `@uuid`

# GraphQL API Generation Recipe

- Heavenly-x Built-in GraphQL types:

```gql
input WhereInput {
  filter: FilterInput
  orderBy: OrderByInput
  range: RangeInput
  first: Int
  last: Int
  skip: Int
}

input OrderByInput {
  field: String!
  order: OrderEnum
}

enum OrderEnum {
  DESC
  ASC
}

input RangeInput {
  before: ID!
  after: ID!
}


# Example:
# {
#   not: {
#     eq: { field: "name", value: "anas" },
#     and: {
#       matches: ""
#       or: { eq: { field: "age", value: 18 } }
#     }
#   }
# }
input FilterInput {
  not: FilterInput
  and: FilterInput
  or: FilterInput
  eq: ComparisonInput # works only when the field is of type String or Int or Float
  gt: ComparisonInput # works only when the field is of type Float or Int
  gte: ComparisonInput # works only when the field is of type Float or Int
  lt: ComparisonInput # works only when the field is of type Float or Int
  lte: ComparisonInput # works only when the field is of type Float or Int
  matches: MatchesInput # works only when the field is of type String
}

input MatchesInput {
  # could be a single field like "friend" or a path "friend.name"
  # works only when the field is of type String
  field: String! 
  regex: String!
}

input ComparisonInput {
  # could be a single field like "friend" or a path "friend.name"
  # If the type of the field or the path is object,
  # then all fields that exist on value of `value: Any!` must be
  # compared with fields with the same name in the model recursively  
  field: String! 
  value: Any!
}

directive @filter(filter: FilterInput!)
directive @order(order: OrderEnum!) on FIELD
directive @range(range: RangeInput!) on FIELD
directive @first(first: Int!) on FIELD
directive @last(last: Int!) on FIELD
directive @skip(skip: Int!) on FIELD
directive @listen(to: EVENT_ENUM) on FIELD # on field selections inside a subscription

enum EVENT_ENUM {
  REMOVE
  NEW
  CHANGE
}

scalar Any
```

- For each model there exist:

  1. ✔️ `{model.id}` output type where each field on this input type respects the requirements of the model. Meaning, required fields will stay required and optional fields will stay optional. Fields with Non-primitive types will be substituted with `{type.id}` with `!` appended if the field is not optional. Fields with list type will take one optional argument `where: WhereInput` for filtering and selecting values. With an optional field 
  ```
  type {model.id} {
    {model.id.small}: {model.id}
    {for field in model.fields.filter(!isList)}
      {field.id}: {field.type.id} {!field.type.isOptional ? "!" : ""}
    {endfor}
    {for field in model.fields.filter(isList)}
      {field.id}(where: WhereInput): {field.type.id} {!field.type.isOptional ? "!" : ""}
    {endfor}
  }
  ```
  2. ✔️ `{model.id}Input` input type where all fields are optional and field of non-primitive types take the type `{field.type.id}Input`.
  ```
  type {model.id}Input {
    {model.id.small}: {model.id}
    {for field in model.fields}
      {field.id}: {field.type.id}
    {endfor}
  }
  ```
  7. `{model.id}Queries` output type which it's shape is generated using the folowing template:
  ```
  type {model.id}Queries {
    read({model.primaryKey}: {model.primaryKey.type.id}!): {model.id}
    list(where: WhereInput): [{model.id}]! # directives: @filter, @order, @range, @first, @last, @skip
  }
  ```
  8. `{model.id}Mutations` output type which it's shape is generated using the folowing template:
  ```
  type {model.id}Mutations {
    login(publicCredential: String, secretCredential: String): String
    create({model.id}: {model.id}Input!): {model.id}
    update({model.primaryField.id}: {model.primaryField.type}!, {model.id}: {model.id}Input!): {model.id}
    upsert({model.id}: {model.id}Input!): {model.id}
    delete({model.primaryField.id}: {model.primaryField.type}!): {model.id}
    createMany({model.id}: [{model.id}Input]!): [{model.id}]
    updateMany({model.id}: [{model.id}Input]!): [{model.id}]
    upsertMany({model.id}: [{model.id}Input]!): [{model.id}]
    deleteMany({model.primaryField.id}: [{model.primaryField.type}]): [{model.id}] # directives: @filter
    {for field in model.field.filter(isList)}
      pushTo{field.id}(item: {field.type.id}Input!): {field.type}
      pushManyTo{field.id}(items: [{field.type.id}Input!]!): {field.type}
      deleteFrom{field.id}({field.type.primaryField.id}: {field.type.primaryField.type}!): {field.type.id}
      deleteManyFrom{field.id}({field.primaryField.id}: [{field.type.primaryField.type}]): [{field.type.id}] # directives: @filter
    {endfor}
    recover({model.primaryField.id}: {model.primaryField.type}!): {model.id}
    recoverMany({model.primaryField.id}: [{model.primaryField.type}]): [{model.id}] # directives: @filter
  }
  ```
  9. `{model.id}Subscriptions` output type which it's shape is generated using the folowing template:
  ```
  type {model.id}Subscriptions {
    read({model.primaryKey}: {model.primaryKey.type}!): {model.id}
    list(where: WhereInput): {model.id} # directives: @filter, @order, @range, @first, @last, @skip
  }
  ```
  10. `{model.id}` query that returns `{model.id}Queries`
  11. `{model.id}` mutation that returns `{model.id}Mutations`
  12. `{model.id}` subscription that returns `{model.id}Subscriptions`

  ## TODO:

  - ✔️ Think about having nested filtering mechanism for fields with list type. Maybe like each list field can take one optional argument `where: WhereInput`