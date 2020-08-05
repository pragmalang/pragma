package running

import domain._
import org.scalatest.flatspec.AnyFlatSpec

class PermissionTree extends AnyFlatSpec {
  "Permission tree" should "be generated correctly" in {
    val code = """
    @1 @user model User {
        @1 username: String @publicCredential @primary
        @2 password: String @secretCredential
        @3 friend: User?
        @4 todos: [Todo]
    }

    @2 @user model Admin {
        @1 username: String @publicCredential @primary
        @2 password: String @secretCredential
    }
    
    @3 model Todo {
        @1 title: String @primary
        @2 content: String
    }

    allow CREATE User

    role User {
        allow ALL self.todos
        allow CREATE Todo
    }

    role Admin {
        allow ALL User
        deny READ User.password
        allow ALL Todo 
    }
    """

    val syntaxTree = SyntaxTree.from(code).get
    val permissions = syntaxTree.permissions

    assert(permissions.tree(None)("User")(Create).length == 1)

    assert(
      permissions.tree(Some("Admin"))("User")(Read)(1).ruleKind == Deny
    )
  }
}
