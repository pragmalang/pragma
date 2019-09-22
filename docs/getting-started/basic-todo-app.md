# Basic Todo App

In this tutorial, we'll create a todo application with user authentication. A user can have many todos, and they can only access their own todos.

Let's import our JavaScript files

```heavenly-x
import "./setters.js" as setters
import "./getters.js" as getters
import "./validators.js" as validators
import "./auth.js" as auth
```

Then, we can start defining our `User` [model](../fundamentals/user-models.md):

```heavenly-x
@user
model User {
    username: String @publicCredential
    password: String @secretCredential
    todos: [Todo]
}
```
Notice the `@user` syntax. This is a [directive](../fundamentals/directives.md) that tells Heavenly-x that this is a [user model](../fundamentals/user-models.md).

Now we define the `Todo` model:

```heavenly-x
model Todo {
    title: String
    content: String
    status: TodoStatus = "DONE"
    user: User
}

enum TodoStatus {
    DONE
    INPROGRESS
    TODO
}
```

Notice the `TodoStatus` enum. [Enums](../fundamentals/enum-types.md) are definitions of all the possible values that a field can hold.

Ok, now we need to define permissions. Our requirements dictate that a `User` can only edit, read, write, and delete their own `Todo`s.

```heavenly-x
permit {
    role User {
        [CREATE] Todo(auth.todo.forAuhenticatedUser)
        [READ, UPDATE, DELETE] Todo(auth.todo.belongsToAuthenticatedUser)
    }
}
```

This block contains one section for the `User`, which contains two rules:

1. A user can only create a new todo if `todo.user` is the user creating it.
2. A user can `READ`, `UPDATE`, and `DELETE` a `Todo` if it belongs to their array of `todos`.

Congratulations! Now you have a GraphQL API. 