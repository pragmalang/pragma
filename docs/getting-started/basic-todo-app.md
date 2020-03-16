# Basic Todo App

In this tutorial, we'll create a todo application with user authentication. A user can have many todos, and they can only access their own todos, and anyone can create a user account.

We can start by defining our `User` [model](../features/user-models.md):

```pragma
@user
model User {
  username: String @publicCredential @primary
  password: String @secretCredential
  todos: [Todo] = []
}
```

Notice the `@user` syntax. This is a [directive](../features/directives.md) that tells Pragma that this is a [user model](../features/user-models.md).

Now we define the `Todo` model:

```pragma
model Todo {
  title: String
  content: String
  status: TodoStatus = "TODO"
}

enum TodoStatus {
  DONE
  INPROGRESS
  TODO
}
```

Notice the `TodoStatus` enum. [Enums](../features/enum-types.md) are definitions of all the possible string values that a field can hold.

Ok, now we need to define permissions. Our requirements dictate that a `User` can only edit, read, write, and delete their own `Todo`s, and that anyone can create a user account.

```pragma
allow CREATE User

role User {
  allow MUTATE self.todos
  allow [READ, UPDATE, DELETE] self
  allow UPDATE Todo in self.todos
}
```

This block contains one section for the `User` role, which contains three rules:

1. A user can push new todos and remove existing todos from their `todos` array field
2. A user can `READ`, `UPDATE`, and `DELETE` their own data
3. A user can `UPDATE` a todo if it's in their list of todos

Congratulations! Now you have a GraphQL API, and you can run queries against it like:
1. Creating a new `User`
```graphql
mutation {
  User {
    create(user: { username: "john", password: "123456789" }) {
      username
    }
  }
}
```
2. Adding new todos to the user we created
```graphql
mutation {
  User {
    pushToTodos(item: { title: "Finish homework", content: "" }) {
      title
      content
    }
  }
}
```
3. List the list of todos
```
{
  User {
    read(username: "john") {
      todos {
        title
        content
      }
    }
  }
}
```