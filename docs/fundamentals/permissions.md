# Permissions

A `permit` block can be used to define data access permissions for specific user types. Heavenly-x supports Role-Based Access Control (RBAC), and Attribute-Based Access Control (ABAC).

## Role-Based Access Control

A `permit` block can be divided into scopes (a scope for each user model.) These can be thought of as *roles*. Each role has its own access rules, for example:

```heavenly-x
@user
model Instructor {
    name: String @publicCredential
    password: String @secretCredential
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

permit {
    user Instructor {
        
    }
}
```

```heavenly-x
model Book {
    title: String
    description: String
    amazonLink: String
}

@user
model Author {
    username: String @publicCredential
    password: String @secretCredential
    books: [Book]
}

@user
model Admin {
    username: String @publicCredential
    password: String @secretCredential
}

permit {
    [READ] Book

    user Author {
        [CREATE] Book
        [UPDATE, DELETE] Book((author, book) => author.books.contains(book))
    }

    user Admin {
        [ALL] Book
        [ALL] Author
    }
}
```

In this example, we define 3 models: `Book`, `Author`, and `Admin`. See the models section if you see somthing you don't recognize.

The interesting part is the `permit` block, where we define access rules for all users of the application. Let's walk through every section of the block and see what it means:

1. The global scope: this scope contains rules that are applied to all users of the application (even anonymous users.) In this example, it contains a rule that says everyone can view the books in the database.
2. 