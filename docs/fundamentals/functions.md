# Functions

Functions are first-class citizens in Heavenly-x.

```heavenly-x
const olderThan18 = (user: User) => user.age >= 18
```

When binding functions to constants, the argument types of the functions must be specified.


There are only two cases where you don't need to specify the function's argument types:

1. When passing function literals to native higher-order functions (e.g. `map`, `filter`, and `reduce`.)  For example: `grades.filter(grade => grade.mark > 50)`, where grades is an array.

2. When passing function literals to directives (e.g. `@transform` and `@validate`.)