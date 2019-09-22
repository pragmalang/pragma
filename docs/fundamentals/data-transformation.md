# Data Transformation

Data transformation can be done in two ways:

- **[Pre-write](#pre-write-data-transformtion-using-set)**: Before persisting data to the database
- **[Post-read](#post-read-data-transformation-using-get)**: After querying data from the database

## Pre-write data transformtion using `@set`

Say we have a `User` model and we to want convert all usernames to lowercase before they reach the database. In this case we will use the [`@set`](./directives.html#set-field-level) directive

```heavenly-x
import "setters.js" as setters
import "getters.js" as getters

model User {
    @publicCredential
    @set(setters.setUsername)
    username:  String

    password: String @secretCredential

    balance: Float
}
```

`setUsername` is a normal JavaScript function in `setters.js`:

```js
const setUsername = ({ value: username }) => username.toLowerCase();
```

The [`@set`](./directives.html#set-field-level) directive is used to transform the values of incoming data before persisting it to the database. We can also do validations on the field inside the `setUsername` JavaScript function by throwing an error.

## Post-read data transformtion using `@get`

Now say we want the exact same value of `balanace` stored in the database when it's created/updated, but we want to floor it when we query it. In this case we will use the [`@get`](./directives.html#get-field-level) directive.

```heavenly-x
import "setters.js" as setters
import "getters.js" as getters

model User {
    @publicCredential
    @set(setters.setUsername)
    username:  String

    @get(getters.getBalance)
    balance: Float
}
```

`getBalance` is a normal JavaScript function in `getters.js`:

```js
const setUsername = ({ value: balance }) => Math.floor(balance);
```

The [`@get`](./directives.html#get-field-level) directive allows us to transform values when they are queried without mutating them in the database.
