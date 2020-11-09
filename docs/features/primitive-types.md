# Primitive Types

Pragma supports many primitive types that can be used in model field definitions.

## String
Represents a sequence of utf-8 characters. `String` values can be expressed using `String` literals, for example: `"Hello"`.

## Int
Represents a 64-bit whole number. `Int` values can be expressed with `Int` literals, for example: `42`.

## Float
Represent a 64-bit floating point number. `Float` values can be expressed as literals, such as `20.3`.

## Boolean
Represents a value that can be either true or false. `Boolean` values can be expressed using the boolean literals `true` and `false`.

## Array
Ordered homogeneous sequences of elements. You can define an array of `T` elements as `[T]` just like GraphQL SDL.

## Optional
An optional value is a value that might be `null` (might not exist). You can define optional fields by appending a `?` to it's type. For example: `age: Int?`
