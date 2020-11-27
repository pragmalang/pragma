---
id: permissions
title: Permissions
---

Most applications need [authorization](https://en.wikipedia.org/wiki/Authorization) logic to control which *users* can access which data (*resources*) with which *operations*.

- Who: The user
- What: The resource
- How: The operation performed on the resource

One way of setting up autorization for an app is using [Access Control Lists](https://en.wikipedia.org/wiki/Access-control_list) (ACLs), which are powerful since they allow us to define rules for data access in a very declaritive manner. ACLs are used in Unix-like systems for [filesystem permissions](https://en.wikipedia.org/wiki/File_system_permissions#Permissions), and they're powerful and reliable.

## Example Online Course App

Let's say we're designing an online course management app. Each instructor teaches one course, and they control which students can enrol in that course. Instructors, students, and courses can be modeled as follows:

```pragma
@user
@1 model Instructor {
  @1 id: String @uuid @primary
  @2 name: String @publicCredential
  @3 password: String @secretCredential
  @4 course: Course
}

@user
@2 model Student {
  @1 id: String @uuid @primary
  @2 name: String @publicCredential
  @3 password: String @secretCredential
}

@3 model Course {
  @1 id: String @uuid @primary
  @2 name: String
  @3 participants: [Student]
}
```

Here we define two user models: `Instructor` and `Student`, and one non-user model: `Course`.

Now that the data models have been defined, we can start specifying the permissions we're giving to each kind of user.

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

Let's say that we want to restrict `Instructor`s to accessing the course that belongs to them. We can do this by passing a predicate (a function that returns `true` or `false`) in which we compare the course that the `Instructor` is trying to access with the instructor's `course` field.

```pragma
import "./auth-rules.js" as auth { runtime = "nodejs:14" }

role Instructor {
  allow ALL Course auth.courseBelongsToInstructor
  allow ALL Student
}
```

`auth.courseBelongsToInstructor` is a JavaScript function defined in `./auth-rules.js`:

```js
export const courseBelongsToInstructor = 
  ({ instructor, course }) =>  
    ({ result: course.id === instructor.course.id })
```

### Anonymous/Unauthenticated Users

Now we want to allow anonymous (unauthenticated) users to sign up as a `Student`. This means that we need to allow anybody to create a new `Student`:

```pragma
role Instructor {
  allow ALL Course auth.courseBelongsToInstructor
  allow ALL Student
}

allow CREATE Student
```

Notice that the newly added rule is not inside any `role` block. This tells Pragma that anyone can create a `Student`, even if there is not user model defined for them.