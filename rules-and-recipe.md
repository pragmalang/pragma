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
- Fields annotated with `@primary` must only be of type `Integer` or `String`
- Every model must have one and only one field annotated with `@primary`, if not a field called `_id: String @primary @id` is created
- If a plural is specified for a model using the `@plural(name: String)` directive it must not match the name of any other defined model
- Every user is prohibited from accessing any resource by default unless:
  - They are their exist an access rule that allows them to access it
  - The resource that is being accessed is under the hierarchy of their user model (can be turned off using a config entry)
- The built-in hook function `ifSelf` can only be applied to access rules where the resource is of the same type as the role of the enclosing role block. A valid example would be
- Every model or enum should have a unique case-insensitive name, meaning, `user` is the smae as `User`. This is important because in many parts of Heavenly-x's codebase `id`s in general are treated in a case-insensitive manner because of some design choices.
- A user model can have one or more fields annotated with `@publicCredential`
- A user model can have one or zero fields annotated with `@secretCredential`
- A field of type `Integer` annotated with `@id` is the same as a field of type `Integer` annotated with `@unique` and `@autoIncrement`
- A field of type `String` annotated with `@id` is the same as a field of type `String` annotated with `@unique` and `@uuid`
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
  7. `{model.id}Queries` type which it's shape is generated using the folowing template:
  ```
  type {model.id}Queries {
    read({model.primaryKey}: {model.primaryKey.type}!): {model.id}
    list(where: WhereInput): [{model.id}]!
  }
  ```
  8. `{model.id}Mutations` type which it's shape is generated using the folowing template:
  ```
  type {model.id}Mutations {
    login(publicCredential: String, secretCredential: String): String
    create({model.id}: {model.id}ObjectInput!): {model.id}
    update({model.primaryField}: {model.primaryField.type}!, {model.id}: {model.id}OptionalInput!): {model.id}
    upsert({model.id}: {model.id}OptionalInput!): {model.id}
    delete({model.primaryField}: {model.primaryField.type}!): {model.id}
    createMany({model.id}: [{model.id}ObjectInput]!): [{model.id}]
    updateMany({model.id}: [{model.id}ReferenceInput]!): [{model.id}]
    upsertMany({model.id}: [{model.id}OptionalInput]!): [{model.id}]
    deleteMany({model.primaryField}: [{model.primaryField.type}]!): [{model.id}]
  }
  ```
  9. `{model.id}Subscriptions` type which it's shape is generated using the folowing template:
  ```
  type {model.id}Subscriptions {
    read({model.primaryKey}: {model.primaryKey.type}!): {model.id}
    list(where: WhereInput): [{model.id}]!
  }
  ```
  10. `{model.id}` query that returns `{model.id}Queries`
  11. `{model.id}` mutation that returns `{model.id}Mutations`
  12. `{model.id}` subscription that returns `{model.id}Subscriptions`
  ## TODO:

  - ✔️ Think about having nested filtering mechanism for fields with list type. Maybe like each list field can take one optional argument `where: WhereInput`
