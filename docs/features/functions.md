# Functions

In Pragma, you can use functions written in many languages for your data validation/transformation and user authorization. Functions can be imported using an `import` statement such as:

```pragma
import "./js-functions.js" as jsFunctions { runtime = "nodejs:14" }
import "./py-functions.py" as pyFunctions { runtime = "python:3" }
```
Where `./js-functions.js` and `./py-functions.py` are files at the same location as your project's `Pragmafile`. Note that Pragma will also look for files in your project's directory even if their paths don't start with `./`.

An `import` consists of three components:
* The location of the function code (file or directory)
* The name of the import (the identifier after `as`)
* A configuration block for the import, containing keys and values

An import's name can be used to reference all the functions defined within the specified code path. For example:
```pragma
jsFunctions.myJsFunction
```
These references to functions can be passed to directives, such as `@onRead`, or authorization rules.

You can find full examples of Pragma projects using functions [here](https://github.com/pragmalang/examples).

## JavaScript Functions
You can import a single JavaScript file containing the function definitions that you want to reference by the import's name. Alternatively, you can import an NPM package, which is a directory containing:

* A valid `package.json` file that **must** contain a `main` entry (typically `index.js`)
* The file specified in the `main` entry of the `project.json` file, exporting any functions you would like to reference in your `Pragmafile`
* Any NPM dependencies you're using, all inside the `node_modules` directory (you need to run `npm install`. Pragma doesn't automatically install dependencies in `package.json`)
