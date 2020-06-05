package running

import org.scalatest.FlatSpec
import domain._

class PermissionTree extends FlatSpec {
  "Permission tree" should "be generated correctly" in {
    val code = """
    @1 @user model User {
        username: String @publicCredential
        password: String @secretCredential
        friend: User?
        todos: [Todo]
    }

    @2 @user model Admin {
        username: String @publicCredential
        password: String @secretCredential
    }
    
    @3 model Todo {
        title: String
        content: String
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
