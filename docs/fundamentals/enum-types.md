# Enum Types

An *enum* is a definition of all possible values of a particular type. For example:

```heavenly-x
enum WeekDay {
    SUNDAY
    MONDAY
    TUESDAY
    WEDNESDAY
    THURSDAY
    FRIDAY
    SATURDAY
}
```

Enum values are dealt with as regular `String` values, so the above definition is equivalent to:

```heavenly-x
enum WeekDay {
    "SUNDAY"
    "MONDAY"
    "TUESDAY"
    "WEDNESDAY"
    "THURSDAY"
    "FRIDAY"
    "SATURDAY"
}
```