# Functions

Functions are first-class citizens in Heavenly-x. This means that you can use them like any other value (pass them to other functions, assign them to contants, etc.)

```heavenly-x
const olderThan18 = (user: User) => user.age >= 18

@validate(validator: olderThan18, errorMessage: (_) => "User is underage.")
model User {
    name: String
    age: Integer
}
```

When binding functions to constants, the types of the arguments of the functions must be specified, but they're unnecessary when passing function literals to other functions (the type information is already provided in the higher-order function's signature.)