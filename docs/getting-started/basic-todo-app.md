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

# Data validation and transformation

You can validate data using the functions you pass to directives such as `onWrite` and `onRead`. For example:

```pragma
import "./validators.js" as validators { runtime= node }

@onWrite(validators.validateBook)
@1 model Book {
  @1 title: String
  @2 authors: [String]
}
```

where `validateBook` is a JavaScript function in `validators.js`, defined as:

```js
const validateBook = book => {
  if(book.authors.length === 0) {
    throw new Error("A book must have at least one author")
  }
  return book
}
```

This function returns a new version of the input book that is then saved to the database. Notice how this function throws an error if the input book's `authors` array is empty. When the error is thrown, the request fails, and the thrown error's message is returned to the user. Similarly, you can transform the data going out of the database using the functions you pass to `onRead`. For example:

```pragma
import "./transformers.js" as transformers

@onRead(transformers.transformBook)
@1 model Book {
  @2 title: String
  @3 authors: [String]
}
```

where `transformBook` is a JavaScript function defined in `transformers.js` as:

```js
const transformBook = book => ({ ...book, title: book.title.toUpperCase() })
```

The result of the transformation is then returned to the user, or passed to subsequent read hooks if specified by adding more `onRead` directives to the model. The same composition mechanism applies to other types of directives that take function arguments, i.e. `onWrite`, `onLogin`, and `onDelete`.