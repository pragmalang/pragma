# Validation Rules

- ✔️ `T` types are mapped to `T!` GraphQL types
- ✔️ `[T]` types are mapped to `[T]!` GraphQL types
- ✔️ `T?` types are mapped to `T` GraphQL types
- ✔️ `[T]?` types are mapped to `[T]` GraphQL types
- The following is list of reserved type names:
  - `Subscription`
  - `Query`
  - `Mutations`
  - `WhereInput`
  - `OrderEnum`
  - `SingleRecordEvent`
  - `MultiRecordEvent`
  - `RangeInput`
  - `EqInput`
  - `WhereInput`
  - `OrderEnum`
  - `OrderByInput`
  - `LogicalFilterInput`
  - `PredicateInput`
  - `Any`
- If there is no `permit` block then everything is permitted (Authorization always return `true`)
- If there exist `CREATE SomeType.someField` an error must be thrown since you can only update a field but you can't create one
- If a type has a recursive field (field of the same type), this field must be optional
- If there's a circular dependency between two types `A` and `B`, one of the dependant fields must be optional. (Not sure about this rule, check Prisma)
- Fields with `@publicCredential` must ALWAYS be of type `String` or `String?`
- Fields with `@secretCredential` must ALWAYS be of type `String`
- The client should be able to perform mutations with full objects or ID strings (DB references) as input values. This can be solved with a scalar input type
- Each model is allowed to have only one field annotated with `@primary`
- Fields annotated with `@primary` must only be of type `Integer` or `String`
- Every model must have one and only one field annotated with `@primary`, if not a field called `_id: String @primary @id` is created
- If a plural is specified for a model using the `@plural(name: String)` directive it must not match the name of any other defined model

# GraphQL API Generation Recipe

- Heavenly-x Built-in GraphQL types:

```gql
input EqInput {
  field: String!
  value: Any!
}

input WhereInput {
  filter: LogicalFilterInput
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

input LogicalFilterInput {
  AND: [LogicalFilterInput]
  OR: [LogicalFilterInput]
  predicate: FilterInput
}

input FilterInput {
  eq: EqInput
}

enum MultiRecordEvent {
  CREATE
  UPDATE
  READ
  DELETE
}

enum SingleRecordEvent {
  UPDATE
  READ
  DELETE
}

scalar Any
```

- For each model there exist:

  1. ✔️ `{model.id}` output type where each field on this input type respects the requirements of the model. Meaning, required fields will stay required and optional fields will stay optional. Fields with Non-primitive types will be substituted with `{type.id}` with `!` appended if the field is not optional. Fields with list type will take one optional argument `where: WhereInput` for filtering and selecting values
  2. ✔️ `{model.id}ObjectInput` input type where each field on this input type respects the requirements of the model. Meaning, required fields will stay required and optional fields will stay optional. Fields with Non-primitive types will be substituted with `{type.id}ReferenceInput` with `!` appended if the field is not optional.
  3. ✔️ `{model.id}OptionalInput` input type. Exact copy of `{model.id}ObjectInput` except that all fields are optional
  4. ✔️ `{model.id}ReferenceInput` input type. Exact copy of `{model.id}OptionalInput` except that the primary field is required.
  5. ✔️ `{model.id}Notification` output type. It has two fields `event: MultiRecordEvent!`, and `{model.id}: {model.id}!`.
  6. ✔️ `create{model.id}` mutation. It takes one required argument
     `{model.id}: {model.id}ObjectInput!`
     and returns
     `{model.id}`
  7. ✔️ `update{model.id}` mutation. It takes two required arguments
     `{model.primaryField}: {model.primaryField.type}!`,
     `{model.id}: {model.id}OptionalInput!`
     and returns
     `{model.id}`
  8. ✔️ `upsert{model.id}` mutation. It takes one required argument
     `{model.id}: {model.id}OptionalInput!`
     and returns
     `{model.id}`
  9. ✔️ `delete{model.id}` mutation. It takes one required argument
     `{model.primaryField}: {model.primaryField.type}!`
     and returns
     `{model.id}`
  10. ✔️ `createMany{model.id}` mutation. It takes one required argument
      `{model.id}: [{model.id}ObjectInput]!`
      and returns
      `[{model.id}]`
  11. ✔️ `updateMany{model.id}` mutation. It takes one required argument
      `{model.id}: [{model.id}ReferenceInput]!`
      and returns
      `[{model.id}]`
  12. ✔️ `upsertMany{model.id}` mutation. It takes one required argument
      `{model.id}: [{model.id}OptionalInput]!`
      and returns
      `[{model.id}]`
  13. ✔️ `deleteMany{model.id}` mutation. It takes one required argument
      `{model.primaryField}: [{model.primaryField.type}]!`
      and returns
      `[{model.id}]`
  14. ✔️ `{model.id}` query. It takes one required argument `{model.primaryKey}: {model.primaryKey.type}!`, and returns `{model.id}`
  15. ✔️ `{model.id.pluralize}` query. It takes one optional argument `where: WhereInput`, and returns `[{model.id}]`. Note: You cannot supply `range` when `first` or `last` are applied, if you do so, a runtime error will be thrown. You can use this query for pagination, similar to the usage in Prisma https://www.prisma.io/docs/prisma-client/basic-data-access/reading-data-TYPESCRIPT-rsc3/#pagination .
  16. ✔️ `count{model.id.pluralize}`. Similar to the input of `{model.id.pluralize}` query and returns `Int`. It returns the length of the selected list.
  17. ✔️ `{model.id}Exists` query. It takes one required argument `filter: LogicalFilterInput`, and returns `Int!` that represents the number of records to which the `predicate` is true.
  18. `{model.id}` subscription. It takes two optional arguments `{model.primaryKey}: {model.primaryKey.type}` and, `on: [SingleRecordEvent!]`. Returns `{model.id}Notification`. If `on` is not supplied then the default value is `[READ, UPDATE, DELETE]`
  19. `{model.id.pluralize}` subscription. It takes two optional arguments `where: WhereInput`, and `on: [MultiRecordEvent!]`. Returns `[{model.id}Notification!]!`. If `on` is not supplied then the default value is `[CREATE, UPDATE, DELETE, READ]`.

  ## TODO:

  - ✔️ Think about having nested filtering mechanism for fields with list type. Maybe like each list field can take one optional argument `where: WhereInput`
