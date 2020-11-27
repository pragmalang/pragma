---
id: basic-todo-app
title: Basic Todo App
---

In this tutorial, we'll create a todo application with user authentication. A user can have many todos, and they can only access their *own* todos, and anyone can create a user account.

To start, initialize a new Pragma project by running:
```sh
pragma new project
```
After answering Pragma's questions, there will be a directory with the name of your project containing a `Pragmafile`, where you'll be writing all your Pragma code. Now we can define our `User` [model](../features/user-models.md) in the `Pragmafile`:

```pragma
@user
@1 model User {
  @1 username: String @publicCredential @primary
  @2 password: String @secretCredential
  @3 todos: [Todo]
}
```

Notice the `@1`, `@2`, on the `User` model and it's fields, these are called **indecies** and they are important for Pragma to be able to perform database migrations automatocally.

Notice also the `@user` syntax. This is a [directive](../features/directives.md) that tells Pragma that this is a [user model](../features/user-models.md), so Pragma would set up auth workflows for this model.

Now we define the `Todo` model:

```pragma
@2 model Todo {
  @1 id: String @uuid @primary
  @2 title: String
  @3 content: String
  @4 status: TodoStatus = "TODO"
}

enum TodoStatus {
  DONE
  INPROGRESS
  TODO
}
```

[`enum`s](../features/enum-types.md) are definitions of all the possible string values that a field can hold.

Ok, now we need to define permissions. Our requirements dictate that a `User` can only edit, read, write, and delete their own `Todo`s, and that anyone can create a user account.

```pragma
allow CREATE User

role User {
  allow MUTATE self.todos
  allow [READ, UPDATE, DELETE] self
}
```

The first line is a definition of an access rule that applies to *anonymous users*; it says anonymous users of your API can create new `User` records. After that comes a *role* definition. This block contains one section for the `User` role, which contains three rules:

1. When a `User` is logged in, they can push new todos and remove existing todos from their `todos` array field
2. When a `User` is logged in, they can `READ`, `UPDATE`, and `DELETE` their own data

But still, we need to tell Pragma that a user can `UPDATE` a `Todo` only if it's in their list of `todos`. We're going to write a function that checks whether a todo is in the current `User`'s list of todos.

We'll create a file called `functions.js` in the same directory as the `Pragmafile` containing:

```js
// functions.js
const selfOwnsTodo = ({ user, todo }) => {
  const userTodoIds = user.todos.map(todo => todo.id)
  if (userTodoIds.contains(todo.id)) {
    return { result: true }
  }
  return { result: false }
}
```

Now that we've defined `selfOwnsTodo`, let's use it in the `User` role:

```
import "./functions.js" as fns { runtime = "nodejs:14" }

role User {
  allow MUTATE self.todos
  allow [READ, UPDATE, DELETE] self
  allow UPDATE Todo if fns.selfOwnsTodo
}
```

## A Note on Authorization Predicates

In the example above we're returning a JSON object of the shape `{ result: boolean }`, not a `boolean` value directly, this is because all imported functions are run as OpenWhisk serverless functions and OpenWhisk requires that all functions must return a JSON object for some reason. This will be solved in the future. **This is only a problem for functions that are used by authorization rules**.

For more information about how authorization rules work with functions, see the [Permissions section](../features/permissions.md).

Alright, now that we've done all the "hard work," we can start our server by running the following command in the root of our project:
```sh
pragma dev
```

Congratulations! Now if you follow the URL printed out in your terminal, you'll find a GraphQL Playground where you can run queries/mutations such as:

#### Creating a new `User`
```graphql
mutation {
  User {
    create(user: { username: "john", password: "123456789" }) {
      username
    }
  }
}
```

### Login as `john`
```graphql
mutation {
  User {
    loginByUsername(username: "john", password: "123456789")
  }
}
```

and we'll get a JWT token from the server:
```json
{
  "data": {
    "User": {
      "loginByUsername": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VybmFtZSI6ImpvaG4iLCJyb2xlIjoiVXNlciJ9.bfqwEcsRZJfdhhY3K83C-wOKa3JmUbfSHF7BCKmNqiU"
    }
  }
}
```

### Adding new todos to `john`
```graphql
mutation {
  User {
    pushToTodos(username: "john", item: { title: "Finish homework", content: "" }) {
      title
      content
    }
  }
}
```

We need to add an authorization header containing the JWT token that was returned from the `loginByUsername` mutation
```json
{
  "Authorization": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VybmFtZSI6ImpvaG4iLCJyb2xlIjoiVXNlciJ9.bfqwEcsRZJfdhhY3K83C-wOKa3JmUbfSHF7BCKmNqiU"
}
```

#### List `john`'s todos
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

We need to put the JWT token in the `Authorization` header here too 
```json
{
  "Authorization": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VybmFtZSI6ImpvaG4iLCJyb2xlIjoiVXNlciJ9.bfqwEcsRZJfdhhY3K83C-wOKa3JmUbfSHF7BCKmNqiU"
}
```
