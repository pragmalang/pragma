# Getting Started

## Install Pragma

> Pragma is currently under heavy development, and should not be used in a production setting. All Pragma APIs are subject to breaking change.

### Requirements
Pragma requires [Docker](https://docs.docker.com/get-docker/) and [Docker Compose](https://docs.docker.com/compose/install/) on the `PATH`. To make sure you have them and that they work, run:
```sh
docker run hello-world

docker-compose --help
```
If either command fails, make sure it works before proceeding with the installation of Pragma.

### Installation
Pragma currently works only on Linux. To install it, run:
```sh
curl -o pragma https://pragmalang.github.io/releases/linux/pragma && chmod +x pragma && sudo mv pragma /usr/local/bin/
```

This will download the Pragma binary, change it to become executable, and place it in `/usr/local/bin`. Once Pragma is downloaded and installed, you can see if it works by running `pragma help`.

## Basic Todo App

In this tutorial, we'll create a todo application with user authentication. A user can have many todos, and they can only access their *own* todos, and anyone can create a user account.

To start, initialize a new Pragma project by running:
```sh
pragma init
```
After answering Pragma's questions, there will be a directory with the name of your project containing a `Pragmafile`, where you'll be writing all your Pragma code, and a `docker-compose.yml` file. `cd` into the project's directory and run `docker-compose up -d` to start the Pragma daemon (which runs in the background and never bothers you.)

After the project has been created and the daemon has been started, we can define our `User` [model](../features/user-models.md):

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
@1 model Todo {
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

This block contains one section for the `User` role, which contains three rules:

1. A user can push new todos and remove existing todos from their `todos` array field
2. A user can `READ`, `UPDATE`, and `DELETE` their own data

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

### A Note on Authorization Predicates

Notice that in the example above we're returning a JSON object of the shape `{ result: boolean }`, not a `boolean` value directly, this is because all imported functions are run as OpenWhisk serverless functions and OpenWhisk requires that all functions must return a JSON object for some reason. This will be solved in the future. **This is only a problem for functions that are used by authorization rules**.

For more information about how authorization rules work with functions go to  

Then let's use this function the `User` role:

```
import "./functions.js" as fns { runtime = "nodejs:14" }

role User {
  allow MUTATE self.todos
  allow [READ, UPDATE, DELETE] self
  allow UPDATE Todo if fns.selfOwnsTodo
}
```

Alright, now that we've done all the hard work, we can start our server by running the following command in the root of our project:
```sh
pragma dev
```

Congratulations! Now if you follow the URL printed out in your terminal, you'll find a GraphQL Playground where you can run queries such as:

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

#### Login as `john`
```graphql
mutation {
  User {
    login(username: "john", password: "123456789")
  }
}
```

and we'll get a JWT token from the server:
```json
{
  "data": {
    "User": {
      "login": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VybmFtZSI6ImpvaG4iLCJyb2xlIjoiVXNlciJ9.bfqwEcsRZJfdhhY3K83C-wOKa3JmUbfSHF7BCKmNqiU"
    }
  }
}
```

#### Adding new todos to `john`
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

We need to add an authorization header containing the JWT token that was returned from the `login` mutation
```json
{
  "Authoriztion": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VybmFtZSI6ImpvaG4iLCJyb2xlIjoiVXNlciJ9.bfqwEcsRZJfdhhY3K83C-wOKa3JmUbfSHF7BCKmNqiU"
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
  "Authoriztion": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VybmFtZSI6ImpvaG4iLCJyb2xlIjoiVXNlciJ9.bfqwEcsRZJfdhhY3K83C-wOKa3JmUbfSHF7BCKmNqiU"
}
```

## Data Validation and Transformation

### Validation

Just like with authorization rules, we can validate data using functions. For example:

```pragma
import "./validators.js" as validators { runtime = "nodejs:14" }

@onWrite(validators.validateBook)
@1 model Book {
  @1 id: String @uuid @primary
  @2 title: String
  @2 authors: [String]
}
```

where `validateBook` is a JavaScript function in `validators.js`, defined as:

```js
const validateBook = ({ book }) => {
  if(book.authors.length < 1) {
    throw new Error("A book must have at least one author")
  }
  return { book }
}
```

### Transformation

Let's say that we want every book's title to be in uppercase automatically on every read, we can pass a function to `@onRead` directive on the `Book` model

```pragma
import "./transformers.js" as transformers { runtime = "nodejs:14" }

@onRead(transformers.transformBook)
@1 model Book {
  @1 id: String @uuid @primary
  @2 title: String
  @2 authors: [String]
}
```

`transformBook` is a JavaScript function defined in `transformers.js` as:

```js
const transformBook = ({ book }) => ({ ...book, title: book.title.toUpperCase() })
```

The result of the transformation is then returned to the user, or passed to the next `@onRead`. The same composition mechanism applies to other types of directives that take function arguments, i.e. `onWrite`, `onLogin`, and `onDelete`.