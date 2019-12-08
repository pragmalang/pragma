# Permissions

Most applications need [authorization](https://en.wikipedia.org/wiki/Authorization) logic to control which users can access what data (resources).

One way of setting up autorization for an app, is by using [Access Control Lists (ACL)](https://en.wikipedia.org/wiki/Access-control_list). ACLs are powerful since they allow us to define rules for data access in a very declaritive manner. ACLs are used in Unix-like systems for filesystem [permissions](https://en.wikipedia.org/wiki/File_system_permissions#Permissions).

Heavenly-x supports [Role-Based Access Controls (RBAC)](https://en.wikipedia.org/wiki/Role-based_access_control), and [Attribute-Based Access Controls (ABAC)](https://en.wikipedia.org/wiki/Attribute-based_access_control). A `acl` block can be used to define data access permissions for specific user types.

A resource could be an entire model (Ex: `Course`) or a field on some model (Ex: `Course.name`)

In an `acl` block, each user model is considered a _role_. Each role has its own access rules. Example on [RBAC](#role-based-access-control) and [ABAC](#attribute-based-access-control) below.

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
    course: Course
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

You can write an access rule in this format:

```
role <USER_MODEL> {
    allow|deny [<CRUD_OPERATION>*] <RESOURCE>
}
```

or

```
role <USER_MODEL> {
    allow|deny <CRUD_OPERATION> <RESOURCE>
}
```

the `allow` keyword tells Heavenly-x to allow the current user to access the specified `<RESOURCE>` using the specified `<CRUD_OPERATION>`s if and only if the current user is an instance of `<USER_MODEL>`.

the `allow` keyword tells Heavenly-x to allow the current user to access the specified `<RESOURCE>` using the specified `<CRUD_OPERATION>`s if and only if the current user is an instance of `<USER_MODEL>`

Example:

```heavenly-x
acl {
    role Instructor {
        allow ALL Course
        allow ALL Student @ifOwner
        allow ALL Instructor @ifSelf
    }

    role Student {
        allow READ Course @ifOwner
        allow [READ, UPDATE] Student @ifSelf
    }

    CREATE Student
}
```

Let's discuss the part below:

```heavenly-x
role Instructor {
    allow ALL Course
    allow ALL Student @ifOwner
    allow ALL Instructor @ifSelf
}
```

Here we define a `role` block where we specify access rules that allow any `Instructor` to do all CRUD operations on `Course`s and `Student`s using the `allow` keyword. The `@ifOwner` directive on the rule `allow ALL Student @ifOwner` tells Heavenly-x to only allow a `Instructor` to access any `Student` data only if they _own_ this student. We also define an access rule `allow ALL Instructor @ifSelf` that allows a `Instructor` to do all CRUD operations on _their own_ instances in the database using the `@ifSelf` directive

We will explain what the `@ifOwner` and `@ifSelf` directives in a minute.

Now let's move to the next part:

```heavenly-x
role Student {
    allow READ Course @ifOwner
    allow [READ, UPDATE] Student @ifSelf
}
```

Here we define a `role` block for the `Student` role in which we specify an access rule that allows any student to only _read_ courses that the _own_ using the `allow` keyword and the `ifOwner` directive. We also define an access rule `allow [READ, UPDATE] Student @ifSelf` that allows a `Student` to only read and update _their own_ instances in the database using the `@ifSelf` directive.

We also defined a global rule that allows anyone even if they're not users in our system to access the `CREATE` operation on the `Student` model; So they can register as `Student`'s

```heavenly-x
CREATE Student
```

But this will intoduce a bug in our system, anyone even `Student`s and `Instructor`s will be able to create a new `Student` if they want and we only want `Instructor`'s and those who haven't signed up as `Student`s before to be able to create a new `Student` (sign up/register). We will solve this problem by adding a new access rule on the `Student` role that denies their request we they try to create a new `Student` using the `deny` keyword instead of the `allow` keyword

```heavenly-x
role Student {
    allow READ Course @ifOwner
    allow [READ, UPDATE] Student @ifSelf
    deny CREATE Student
}
```

When Heavenly-x sees conflicting rules like the one we added recently and the global one, it chooses to go with the one that denies access to make sure that no resource that is not supposed to be accessed by some set of users can be accessed by them.

> **Note:** The `acl` block is a whitelisting block, meaning, it denies any request if it doesn't match any access rule that allows it. The `deny` keyword is only useful when you want to selectively deny access to a gloabally accessed resource like in the example above.

#### `@ifOwner` Directive

When we define a schema we create tables/collections in the database that respects this schema. Meaning, every table/collection has it's own records/rows/documents (instances) that respects it's coresponding model definition. Each user is just an instance (record/row/document) of it's user model in the database.

When an access rule is annotated with this directive. This tells Heavenly-x to only allow a user to access an instance of a resource (a specific row/document in the DB) if and only if this instance is in the data hierarchy of this user.

Let's explain it by example:
If a user model `A` contains a relation to another model `B` then this will create a hierachy:

```heavenly-x
@user
model A {
    b: B
}

model B {
    aRandomField: String
}
```

now if created an access rule like

```heavenly-x
acl {
    role A {
        allow ALL B @ifOwner
    }
}
```

this means, allow any user of role `A` to access any row/record/document in the `B` table/collection in the data base only if this row/record/document is linked to the current user.

#### `@ifSelf` Directive

This directive is really simple. It tells Heavenly-x "if the current resource that is being accessed is the current user then apply this access rule". This is only useful when used on rules that allows access to a user role to it's own user model.

In the example 


#### CRUD operations

| Operation |
| --------- |
| `READ`    |
| `CREATE`  |
| `UPDATE`  |
| `DELETE`  |

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

The `auth.courseBelongsToInstructor` is a JavaScript function imported from a JavaScript file.

```heavenly-x
permit {
    role Instructor {
        ALL Course(auth.courseBelongsToInstructor)
        ALL Student
    }
}
```

> **Note:** The same requirement can be satisfied using the `@ifOwner` directive but we used predicates in this example to demonstrate how you can define attribute-based access rules
