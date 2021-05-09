---
id: permissions
title: Permissions
---

Most applications need [authorization](https://en.wikipedia.org/wiki/Authorization) logic to control which *users* can access which data (*resources*) with which *operations*.

- Who: The user
- What: The resource
- How: The operation performed on the resource

One way of setting up authorization for an app is using [Access Control Lists](https://en.wikipedia.org/wiki/Access-control_list) (ACLs), which are powerful since they allow us to define rules for data access in a very declarative manner. ACLs are used in Unix-like systems for [filesystem permissions](https://en.wikipedia.org/wiki/File_system_permissions#Permissions), for instance.

For a tutorial where you get to write a Pragma application that uses authorization rules, skip to the [Example Online Course App](#example-online-course-app) section.

## Overview

When a [model](./models.md) is defined, many operations specific to that model are automatically exposed via the GraphQL API, which include `CREATE`, `READ`, `UPDATE`, `DELETE`, and many [other operations](#table-of-available-operations). Using access rules, you can allow or deny an incoming operation based on its kind (e.g. `READ` operation), and its *target resource*, which is either a model field. For instance:
```pragma
allow READ User
```
where `User` is a defined model, means that `READ` operations are allowed on any record of type `User`. Here, `allow` is the kind of the rule, `READ` is the operation type, and `User` is the resource.

The operation type of an access rule can be a single type of event (as in the above example) or you can specify multiple types of operations that the rules matches using array-like syntax. For instance:
```pragma
allow [READ, CREATE] User
```
This rule allows both `READ` and `CREATE` operations on the `User` model.

The resource part of an access rule can refer to an entire model, or it can refer to a particular field of a model:
```pragma
deny READ User.password
```
When the resource part of an access rule is a model, operations performed on any field of the model will match the rule. So if we have `allow READ User`, then `READ` operations are allowed on all fields of the `User` model.

See [the table of operations below](#table-of-available-operations) to see which operations are applicable on which kinds of resources (models/fields). Remember, if an operation is applicable to both model resources (e.g. `User`) and field resources (e.g. `User.username`) but is applied only to a model resource (e.g. `User`) then the rule will cascade to all of that model's fields.
### Everything is Denied by Default

All roles are denied to perform any operation on any resource unless you define an `allow` rule for them to access that exact resource, except for `SET_ON_CREATE` and `READ_ON_CREATE` because they are expected to work when performing a `CREATE` operation and they are there to give you the option to deny them for certain roles on certain resources when you allow the `CREATE` operation for that role since they have no effect unless you allow `CREATE`. See [the table of operations below](#table-of-available-operations).

**Why?** Well, it's a very opinionated decision we took when we decided that. When everything is denied by default, you have to think about every possible action the user *can* take, which can be annoying sometimes. But, on the other hand, this constraint gives us two nice upsides:

- Make the system's code explicit and clear, and the system in general easier to understand and reason about
- Make the system more secure because it forces the developer to follow the [Principle of Least Privilege](https://en.wikipedia.org/wiki/Principle_of_least_privilege)

This will make merely looking at the `Pragmafile` good enough to understand how the system works, which is awesome!

### Table of Available Operations

The following is a table specifying the available permissions, the types of resources they can be applied to, and their default rule kind (`allow` or `deny`):

|  **Permission**  |                **Operation Description**                 |   **Applicable To**   | **Default** |
| :--------------: | :------------------------------------------------------: | :-------------------: | :---------: |
|      `READ`      |                  Retrieve from the API                   | Models & model fields |    deny     |
|     `CREATE`     |            Insert a record into the database             |        Models         |    deny     |
| `READ_ON_CREATE` |    Retrieve after creating with a `CREATE` operation     | Models & model fields |    allow    |
| `SET_ON_CREATE`  | `CREATE` operation that sets a field in the input object |     Model fields      |    allow    |
|     `UPDATE`     |             Modify a record in the database              | Models & model fields |    deny     |
|    `PUSH_TO`     |             Add an element to an array field             |  Model array fields   |    deny     |
|  `REMOVE_FROM`   |          Remove an element from an array field           |  Model array fields   |    deny     |
|     `DELETE`     |            Delete a record from the database             |        Models         |    deny     |
|     `LOGIN`      |              `LOGIN` operation to get a JWT              |      User models      |    deny     |
|      `ALL`       |                      Any operation                       | Models & model fields |    deny     |
### Authorization Predicates

An access rule can be followed by an `if` clause, specifying a condition that must be satisfied in order for the rule to match the operation. These conditions are *predicates*, which are functions that return a boolean value (true or false). Predicates can be imported just like any other function in Pragma, for instance:
```pragma {1, 9}
import "./my-functions.js" as myFunctions { runtime = "nodejs" }

@user @1 model User {
  @1 name: String @primary @publicCredential
  @2 password: String @secretCredential
  @2 age: Int
}

allow CREATE User if myFunctions.ageOver18
```
where `./my-functions.js` is a file containing:
```js
const ageOver18 = user => ({ result: user.age > 18 })

module.exports = { ageOver18 }
```

:::caution
The return of authorization predicates must be an object containing a `result` field of type boolean. If the predicate return anything other than a boolean in the `result` field, it is considered `false`.
:::

:::note
Authorization rules are not the best way to *validate* data coming in, which is basically what `ageOver18` does. It is better to use the [`@onWrite` directive](./directives.md#@onwrite) instead, but this example is implemented using an authorization rule only for demonstration purposes.
:::

### Roles

When a [user model](./user-models.md) is defined, you can define a *role* for that specific user model. A role is a list of rules that apply only to the type of user for which the role is defined. For instance:
```pragma
role User {
  deny READ User.password
}
```
The rules defined within a role are only matched when an authenticated user of type `User` is using the API. 

:::note
Roles can only be defined for user models.
:::

You may have noticed that access rules may be defined outside of a role block. Access rules that do not belong to any role are applied **only** to operations performed by anonymous (unauthenticated) users.

### `self` Rules

Within a `role`, you can define rules that apply only to operations that the user is performing on their own data. For instance:
```pragma
role {
  allow [READ, UPDATE] self
  deny READ self.password
}
```

## Example Online Course App

Let's say we're designing an online course management app. Each instructor teaches one course, and they control which students can enrol in that course. Instructors, students, and courses can be modeled as follows:

```pragma
@user
@1 model Instructor {
  @1 id: String @uuid @primary
  @2 name: String @publicCredential
  @3 password: String @secretCredential
  @4 course: Course
}

@user
@2 model Student {
  @1 id: String @uuid @primary
  @2 name: String @publicCredential
  @3 password: String @secretCredential
}

@3 model Course {
  @1 id: String @uuid @primary
  @2 name: String
  @3 participants: [Student]
}
```

Here we define two user models: `Instructor` and `Student`, and one non-user model: `Course`.

Now that the data models have been defined, we can start specifying the permissions we're giving to each kind of user.

### Defining Access Rules

Access rules can be defined in two places: either globally, or within a `role` block. A `role` block specifies the permissions given to a particular user model, while global rules apply to all users (including anonymous users). For example:

```pragma
role Instructor {
  allow ALL Course
  allow ALL Student
}
```

Here we define a `role` block where we specify that the `Instructor` can do `ALL` CRUD operations with `Course`s and `Student`s.

### Rules with Custom Predicates

Let's say that we want to restrict `Instructor`s to accessing the course that belongs to them. We can do this by passing a predicate (a function that returns `true` or `false`) in which we compare the course that the `Instructor` is trying to access with the instructor's `course` field.

```pragma
import "./auth-rules.js" as auth { runtime = "nodejs" }

role Instructor {
  allow ALL Course auth.courseBelongsToInstructor
  allow ALL Student
}
```

`auth.courseBelongsToInstructor` is a JavaScript function defined in `./auth-rules.js`:

```js
export const courseBelongsToInstructor = 
  ({ instructor, course }) =>  
    ({ result: course.id === instructor.course.id })
```

### Anonymous/Unauthenticated Users

Now we want to allow anonymous (unauthenticated) users to sign up as a `Student`. This means that we need to allow anybody to create a new `Student`:

```pragma
role Instructor {
  allow ALL Course auth.courseBelongsToInstructor
  allow ALL Student
}

allow CREATE Student
```

Notice that the newly added rule is not inside any `role` block. This tells Pragma that anyone can create a `Student`, even if there is not user model defined for them.