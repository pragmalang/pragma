# Basic Todo App

In this tutorial, we'll create a todo application with user authentication. A user can have many todos, and they can only access their own todos.

We can start by defining our `User` [model](../fundamentals/user-models.md):

```
@user
model User {
    username: String @publicCredential
    password: String @secretCredential
    todos: [Todo]
}
```
Notice the `@user` syntax. This is a [directive](../fundamentals/directives.md) that tells Heavenly-x that this is a [user model](../fundamentals/user-models.md).

Now we define the `Todo` model:

```
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
        [CREATE] Todo((user, todo) => todo.user == user)
        [READ, UPDATE, DELETE] Todo(
            (user, todo) => user.todos.contains(todo)
        )
    }
}
```

This block contains one section for the `User`, which contains two rules:

1. A user can only create a new todo if `todo.user` is the user creating it.
2. A user can `READ`, `UPDATE`, and `DELETE` a `Todo` if it belongs to their array of `todos`.

Congratulations! Now you have a GraphQL API. 