# Primitive Types

## String
Represents a sequence of utf-8 characters. `String` values can be expressed using `String` literals, for example: `"Hello"`.

Individual characters of a `String` by their index (starting at zero) can be done using square bracket syntax, for example: `s[2]` will yield the third character of the string.

Accessing a character in a `String` results with an *optional value* of the character. This is to avoid index-out-of-bounds errors, and to ensure type-safety.

**Operators:**
 - `+`: concatenation (`"Hello" + " " + "Heavenly-x!` will yield the string: `"Hello Heavenly-x!"`)

**Methods:**
 - `length(): Integer`: returns the length of the array

 - `slice(from: Integer, to: Integer): String`: returns the characters of the string starting from the character at `from` and ending with the character at `to` (inclusively)

 - `test(subject: String): Boolean`: assumes the calling `String` is a regular expression and returns `true` if the `subject` matches the pattern in the regular expression's  pattern, and `false` otherwise

 - `replace(subject: String, replacement: String): String`: assumes the current `String` is a regular expression and returns a copy of `subject` where every part of `subject` that matches the pattern is replaced with `replacement`

 - `toUpperCase(): String`: returns a version of the `String` with all the characters converted to their uppercase form

 - `toLowerCase(): String`: returns a version of the `String` with all the characters converted to their lowercase form

 - `concat(other: String): String`: returns `other` appended to the calling string
 
 - `charAt(i: Integer): String?`: returns the optional value of the character at index `i` of the calling string

 - `endsWith(postfix: String): Boolean`: returns `true` if the calling string ends with `postfix`, and `false` otherwise

 - `startsWith(prefix: String): Boolean`: returns `true` if the calling string starts with `prefix`, and `false` otherwise

 - `isEmpty(): Boolean`: returns `true` if the calling string contains `0` characters

 - `contains(substring: String): Boolean`: returns `true` if `substring` is a substring of the caller

## Integer
Represents a 64-bit whole number. `Integer` values can be expressed with `Integer` literals, for example: `42`.

**Operators:**
 - `+`: addition
 - `-`: subtraction
 - `*`: multiplication
 - `/`: division
 - `%`: modulo

**Methods:**
 - `toString(): String`: returns the `String` representation of the caller

## Float
Represent a 64-bit floating point number. `Float` values can be expressed as literals, such as `20.3`.

**Operators:**
 - `+`: addition
 - `-`: subtraction
 - `*`: multiplication
 - `/`: division
 - `%`: modulo

**Methods:**
 - `toString(): String`: returns the `String` representation of the caller

 - `floor(): Integer`: rounds the caller down to the nearest whole number

 - `ceiling(): Integer`: rounds the caller up to the nearest whole number

## Boolean
Represents a value that can be either true or false. `Boolean` values can be expressed using the boolean literals `true` and `false`.

**Operators:**:
 - `||`: Logical OR
 - `&&`: Logical AND
 - `!`: Logical NOT (unary)

**Methods**:
 - `toString(): String`: returns the `String` representation of the caller

## Array
Ordered homogeneous sequences of elements of any type. Array elements can be accessed by their index (which starts at zero) using square bracket syntax, for example: `a[1]` is the 2nd element in `a`.

Accessing an element in an array results with an *optional value* of the element. This is to avoid index-out-of-bounds errors, and to ensure type-safety.

Arrays can be expressed using array literals. For example: `[1, 2, 3]` is an array if `Integer`s.

**Operators:**
 - `+`: array concatenation (`[1, 2, 3] + [4, 5, 6] == [1, 2, 3, 4, 5, 6]`)
**Methods:**
> Note: `T` denotes the type of elements in the calling array

 - `contains(element: T): Boolean`: returns `true` if `element` is a member of the caller, and `false` otherwise

 - `length(): Integer`: returns the number of elements in the calling array

 - `elementAt(i: Integer): T?`: returns the optional value of the element at index `i` of the caller

 - `map(transformer: (T) => U): [U]`: returns a version of the calling array with `transformer` applied to all elements such that the resulting array has elements of type `U` (the return type of `transformer`)

 - `filter(predicate: (T) => Boolean): [T]`: returns an array of all elements of the caller where `predicate` holds

 - `reduce(acc: T, (T, T) => T): T`: reduces the array into a single accumulated value that starts as `acc`

## Optional Values
An optional value is a value that might be `Null` (might not exist). You can define optional fields by appending a `?` to it's type. For example: `age: Integer?`

**Methods:**
 - `isNull(): Boolean`: returns `true` if the optional value is actually `Null`, and `false` otherwise

 - `else(alternative: T): T`: returns `alternative` if the actual value of the caller is `Null`, and the actual value of the caller otherwise

## Date
ISO8061-formatted date.

**Operators:**
- `+`: adds the values of two dates

**Methods:**
- `now(): Date`: the current local date/time

- `toString(): String`: returns the `String` representation of the caller

- `ms`

- `sec`

- `min`

- `hour`

- `day`

- `date`

## File
A type that abstracts away the need to set up object storage.

**Methods:**
- `size(): Integer`: returns the caller's size in bytes
- `extension(): String`: the caller file's extension (starting from the first occurrence of '.')