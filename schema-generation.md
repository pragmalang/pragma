# GraphQL APi Schema Generation Rules

- `T` types are mapped to `T!` GraphQL types

- `[T]` types are mapped to `[T]!` GraphQL types

- `T?` types are mapped to `T` GraphQL types

- `[T]?` types are mapped to `[T]` GraphQL types

- The following is a list of reserved type names:
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

- Rules of the form: `CREATE SomeModel.someField` are not allowed since one can update a field's value, but create it

- If a model has a recursive field (field of the same type), this field must be optional

- If there's a circular dependency between two models `A` and `B`, one of the dependant fields must be optional

- Fields with `@publicCredential` must either be of type `String` or `String?`

- Fields with `@secretCredential` must be of type `String`

- The client should be able to perform mutations with full objects or ID strings (DB references) as input values. This can be done with a GraphQL scalar input type

- Fields annotated with `@primary` must only be of type `Int` or `String`

- Every model must have one and only one field annotated with `@primary`. If no such field is defined, the field `_id: String @primary` will be added to the model

- A user is prohibited from accessing any resource unless there exists an access rule that allows them to access it

- A user model must have one or more fields annotated with `@publicCredential`

- A user model must have one or zero fields annotated with `@secretCredential`

- A field of type `Int` annotated with `@id` is the same as a field of type `Int` annotated with `@unique` and `@autoIncrement`

- A field of type `String` annotated with `@id` is the same as a field of type `String` annotated with `@unique` and `@uuid`

# GraphQL API Generation Recipe

- Pragma Built-in GraphQL types:

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
  field: String
  regex: String!
}

input ComparisonInput {
  # could be a single field like "friend" or a path "friend.name"
  # If the type of the field or the path is object,
  # then all fields that exist on value of `value: Any!` must be
  # compared with fields with the same name in the model recursively  
  field: String
  value: Any!
}

enum EVENT_ENUM {
  REMOVE
  NEW
  CHANGE
}

scalar Any

directive @filter(filter: FilterInput!) on FIELD
directive @order(order: OrderEnum!) on FIELD
directive @range(range: RangeInput!) on FIELD
directive @first(first: Int!) on FIELD
directive @last(last: Int!) on FIELD
directive @skip(skip: Int!) on FIELD
directive @listen(to: EVENT_ENUM!) on FIELD # on field selections inside a subscription
```

- For each model, there exists:

  1. `{model.id}` output type where each field on this input type respects the requirements of the model. Meaning that required fields will stay required and optional fields will stay optional. Fields with Non-primitive types will be substituted with `{type.id}` with `!` appended if the field is not optional. Fields with list types will take one optional argument `where: WhereInput` for filtering and selecting values. With an optional field 
  ```
  type {model.id} {
    {for field in model.fields.filter(!isList)}
      {field.id}: {field.type.id} {!field.type.isOptional ? "!" : ""}
    {endfor}
    {for field in model.fields.filter(isList)}
      {field.id}(where: WhereInput): {field.type.id} {!field.type.isOptional ? "!" : ""}
    {endfor}
  }
  ```

  2. `{model.id}Input` input type where all fields are optional, and a field of non-primitive type takes the type `{field.type.id}Input`.
  ```
  input {model.id}Input {
    {for field in model.fields}
      {field.id}: {field.type.id}
    {endfor}
  }
  ```

  3. `{model.id}Queries` output type of the form:
  ```
  type {model.id}Queries {
    read({model.primaryKey}: {model.primaryKey.type.id}!): {model.id}
    list(where: WhereInput): [{model.id}]! # directives: @filter, @order, @range, @first, @last, @skip
  }
  ```

  4. `{model.id}Mutations` output type of the form:
  ```
  type {model.id}Mutations {
    {if model.isUserModel}
      login(publicCredential: String, {model.secretCredential}: String): String
    {endif}
    create({model.id}: {model.id}Input!): {model.id}
    update({model.primaryField.id}: {model.primaryField.type}!, {model.id}: {model.id}Input!): {model.id}
    delete({model.primaryField.id}: {model.primaryField.type}!): {model.id}
    createMany(items: [{model.id}Input]!): [{model.id}]!
    updateMany(items: [{model.id}Input]!): [{model.id}]!
    deleteMany(items: [{model.primaryField.type}!]): [{model.id}]! # directives: @where
    {for field in model.field.filter(isList)}
      pushTo{field.id}(item: {field.type.id}Input!): {field.type.named}
      pushManyTo{field.id}(items: [{field.type.id}Input!]!): {field.type}
      removeFrom{field.id}(item: {field.type.primaryField.type}!): {field.type.named}
      removeManyFrom{field.id}(filter: FilterInput): {field.type} # directives: @filter
    {endfor}
  }
  ```

  5. `{model.id}Subscriptions` output type of the form:
  ```
  type {model.id}Subscriptions {
    read({model.primaryKey}: {model.primaryKey.type}!): {model.id}
    list(where: WhereInput): {model.id} # directives: @filter, @order, @range, @first, @last, @skip
  }
  ```

  6. `{model.id}` query that returns `{model.id}Queries`

  7. `{model.id}` mutation that returns `{model.id}Mutations`
  
  8. `{model.id}` subscription that returns `{model.id}Subscriptions`

# Notes

- `@filter` and `@where` bevahe differently on queries, mutations, and subscriptions. On mutations they are to be used for the server to select records on the database based on the conditions defined by these directives before doing anything to them (delete, update, recover)