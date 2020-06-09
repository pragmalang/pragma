package running

import org.scalatest.FlatSpec
import domain._

class PermissionTree extends FlatSpec {
  "Permission tree" should "be generated correctly" in {
    val code = """
    @1 @user model User {
        @1 username: String @publicCredential
        @2 password: String @secretCredential
        @3 friend: User?
        @4 todos: [Todo]
    }

    @2 @user model Admin {
        @1 username: String @publicCredential
        @2 password: String @secretCredential
    }
    
    @3 model Todo {
        @1 title: String
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
