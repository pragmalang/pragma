# Primitive Types

## String
Represents a sequence of utf-8 characters. `String` values can be expressed using `String` literals, for example: `"Hello"`.

Individual characters of a `String` by their index (starting at zero) can be done using square bracket syntax, for example: `s[2]` will yield the third character of the string.

Accessing a character in a `String` results with an *optional value* of the character. This is to avoid index-out-of-bounds errors, and to ensure type-safety.

**Operators:**
 - `+`: concatenation (`"Hello" + " " + "Heavenly-x!` will yield the string: `"Hello Heavenly-x!"`)

**Methods:**
 - `toString`
 - `length`
 - `slice`
 - `test`
 - `replace`
 - `toUpperCase`
 - `toLowerCase`
 - `concat`
 - `charAt`
 - `endsWith`
 - `startsWith`
 - `isEmpty`
 - `contains`

## Integer
Represents a 64-bit whole number. `Integer` values can be expressed with `Integer` literals, for example: `42`.

**Operators:**
 - `+`: addition
 - `-`: subtraction
 - `*`: multiplication
 - `/`: division
 - `%`: modulo

**Methods:**
 - `toString`
 - `floor`
 - `ceiling`

## Float
Represent a 64-bit floating point number. `Float` values can be expressed as literals, such as `20.3`.

**Operators:**
 - `+`: addition
 - `-`: subtraction
 - `*`: multiplication
 - `/`: division
 - `%`: modulo

**Methods:**
 - `toString`
 - `floor`
 - `ceiling`

## Boolean
Represents a value that can be either true or false. `Boolean` values can be expressed using the boolean literals `true` and `false`.

**Operators:**:
 - `||`: Logical OR
 - `&&`: Logical AND
 - `!`: Logical NOT (unary)

**Methods**:
 - `toString`: converts the value to a `String`

## Array
Ordered homogeneous sequences of elements of any type. Array elements can be accessed by their index (which starts at zero) using square bracket syntax, for example: `a[1]` is the 2nd element in `a`.

Accessing an element in an array results with an *optional value* of the element. This is to avoid index-out-of-bounds errors, and to ensure type-safety.

**Operators:**
 - `+`: array concatenation ()
**Methods:**
 - `contains`
 - `length`
 - `elementAt`
 - `map`
 - `filter`
 - `reduce`

## Optional Values
An optional value is a value that might be `Null` (might not exist). You can define optional fields by appending a `?` to it's type. For example: `age: Integer?`
**Methods:**
 - `isNull`
 - `else`

## Date
ISO8061-formatted date.

**Operators:**
- `+`

**Methods:**
- `now`
- `toString`
- `ms`
- `sec`
- `min`
- `hour`
- `day`
- `date`

## File
A type that abstracts away the need to set up object storage.

**Methods:**
- `size`
- `extension`