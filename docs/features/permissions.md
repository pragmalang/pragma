# Permissions

Most applications need [authorization](https://en.wikipedia.org/wiki/Authorization) logic to control which users can access which data (resources).

One way of setting up autorization for an app, is by using [Access Control Lists (ACL)](https://en.wikipedia.org/wiki/Access-control_list). ACLs are powerful since they allow us to define rules for data access in a very declaritive manner. ACLs are used in Unix-like systems for filesystem [permissions](https://en.wikipedia.org/wiki/File_system_permissions#Permissions).

## Example Online Course App

Let's say we're designing an online course management tool. Each instructor teaches one course, and they control which students enrol in it. Instructors, students, and courses can be modeled as follows:

```pragma
@user
model Instructor {
  name: String @publicCredential
  password: String @secretCredential
  course: Course
}

@user
model Student {
  name: String @publicCredential
  password: String @secretCredential
}

model Course {
  name: String
  participants: [Student]
}
```

Here we define two user models: `Instructor` and `Student`, and one non-user model: `Course`. Now that the data models have been defined, we can start specifying the permissions we're giving to each kind of user.

### Defining Access Rules

Access rules can be defined in two places: either globally, or within a `role` block. A `role` block specifies the permissions given to a particular user model, while global rules apply to all users (including anonymous users). For example:

```pragma
role Instructor {
  allow ALL Course
  allow ALL Student
}
```

Here we define a `role` block where we specify that the `Instructor` can do `ALL` CRUD operations with `Course`s and `Student`s.

### Rules with Custom Predicates

Let's say that we want to restrict `Instructor`s to accessing the course that belongs to them. We can do this by passing a predicate (a function that returns a `true` or `false`) in which we compare the course that the `Instructor` is trying to access with the `Instructor`'s `course` field.

```pragma
role Instructor {
  allow ALL Course auth.courseBelongsToInstructor
  allow ALL Student
}
```

`auth.courseBelongsToInstructor` is just a JavaScript function defined in `./auth-rules.js`:

```js
export const courseBelongsToInstructor = 
  ({ user: instructor, resource: course }) => 
    course._id === instructor.course._id
```

### Global Rules

Now we want to allow any body outside our system to sign up as `Student`s. This means that we need to allow anybody to create a new `Student`:

```pragma
role Instructor {
  allow ALL Course auth.courseBelongsToInstructor
  allow ALL Student
}

allow CREATE Student
```

Notice that the newly added rule is not inside any `role` block. This tells Pragma that anyone can create a `Student`