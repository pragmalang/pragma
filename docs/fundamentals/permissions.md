# Permissions

Most applications need [authorization](https://en.wikipedia.org/wiki/Authorization) logic to control which users can access what data (resources).

One way of setting up autorization for an app, is by using [Access Control Lists (ACL)](https://en.wikipedia.org/wiki/Access-control_list). ACLs are powerful since they allow us to define rules for data access in a very declaritive manner. ACLs are used in Unix-like systems for filesystem [permissions](https://en.wikipedia.org/wiki/File_system_permissions#Permissions).

Heavenly-x supports [Role-Based Access Controls (RBAC)](https://en.wikipedia.org/wiki/Role-based_access_control), and [Attribute-Based Access Controls (ABAC)](https://en.wikipedia.org/wiki/Attribute-based_access_control). A `permit` block can be used to define data access permissions for specific user types.

A resource could be an entire model (Ex: `Course`) or a field on some model (Ex: `Course.name`)

In a `permit` block, each user model is considered a *role*. Each role has its own access rules. Example on [RBAC](#role-based-access-control) and [ABAC](#attribute-based-access-control) below.

## Example

```heavenly-x
import "./auth-rules.js" as auth
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


Here we define two users:
- `Instructor`
- `Student`

and one model:
- `Course`

We will use these models in the upcoming example definitions of permissions:

### Role-Based Access Control

Example:

```heavenly-x
permit {
    role Instructor {
        ALL Course
        ALL Student
    }
}
```

Here we define a `role` block where we specify that the `Instructor` can do `ALL` CRUD operations with `Course`s and `Student`s.

### Attribute-Based Access Control

Let's say that we would like to restrict `Instructor`s to accessing course that belongs to them. We can do this by passing a predicate in which we compare the course that the `Instructor` is trying to access, and the `Instructor`'s `course` field.

```heavenly-x
permit {
    role Instructor {
        ALL Course(auth.courseBelongsToInstructor)
        ALL Student
    }
}
```
Now we want to allow any body outside our system to sign up as `Student`s. This means that we need to allow anybody to create a new `Student`:

```heavenly-x
permit {
    role Instructor {
        ALL Course(auth.courseBelongsToInstructor)
        ALL Student
    }
    CREATE Student
}
```

Notice that the newly added rule is not inside any `role` block. This tells Heavenly-x that anyone can create a `Student`