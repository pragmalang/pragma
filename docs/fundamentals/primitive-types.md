# Primitive Types

## String
Represents a sequence of utf-8 characters. `String` values can be expressed using `String` literals, for example: `"Hello"`.

## Integer
Represents a 64-bit whole number. `Integer` values can be expressed with `Integer` literals, for example: `42`.

## Float
Represent a 64-bit floating point number. `Float` values can be expressed as literals, such as `20.3`.

## Boolean
Represents a value that can be either true or false. `Boolean` values can be expressed using the boolean literals `true` and `false`.

## Array
Ordered homogeneous sequences of elements of any type. 

## Optional Values
An optional value is a value that might be `null` (might not exist). You can define optional fields by appending a `?` to it's type. For example: `age: Integer?`

## Date
ISO8061-formatted date.

## File
A type that abstracts away the need to set up object storage.

**Methods:**
- `size: Integer`: returns the caller's size in bytes
- `extension: String`: the caller file's extension (starting from the first occurrence of '.')