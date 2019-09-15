# Functions

Functions are first-class citizens in Heavenly-x.

```heavenly-x
const olderThan18 = (user: User) => user.age >= 18
```

When binding functions to constants, the argument types of the functions must be specified.

Argument types are unnecessary when passing function literals to other functions since they can be infered (the type information is already provided in the higher-order function's signature.). For example:
```
@user
model Student {
    name: String
    username: String @publicCredential
    password: String @secretCredential

    @transform((grades, self, ctx)
        => grades.filter(grade => grade.mark > 50))
    grades: [Grade]

}
`
model Grade {
    subject: String
    mark: Integer
}
```
