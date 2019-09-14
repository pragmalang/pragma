# Constants

*Constants* are bindings between names and values. These bindings are global, and can be used from within any function in the application.

```heavenly-x
const usernameMaxLength = 30
const lengthErrorMessage = 
    "Error: username should be " + usernameMaxLength.toString() + " characters or less."
```

Functions are first-class citizens in Heavenly-x, so you can assign them to constants:

```
const capitalizePrefix = 
    (text: String) => text.charAt(0) + text.slice(1, text.length())
```