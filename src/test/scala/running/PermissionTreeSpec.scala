package running.authorizer

import domain._
import org.scalatest.flatspec.AnyFlatSpec

class PermissionTreeSpec extends AnyFlatSpec {

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
    allow READ Todo

    role User {
        allow READ self
        deny READ self.password
        allow ALL self.todos
        allow CREATE Todo
    }

    role Admin {
        allow ALL User
        deny READ User.password
        allow ALL Todo 
    }
    """

  implicit val syntaxTree = SyntaxTree.from(code).get
  val permissionTree = new PermissionTree(syntaxTree)
  "Anonymous users" should "only be able to create `User`" in {
    val rules = permissionTree.tree(None)("User")(None)(Create)(false)

    assert(rules.length == 1)
    assert(rules.head.permissions == Set(Create))
    assert(rules.head.resourcePath._1.id == "User")
  }

  "User" should "be able to read self data except `password`" in {
    val userReadSelfPasswordRules =
      permissionTree.tree(Some("User"))("User")(Some("password"))(Read)(true)
    assert(userReadSelfPasswordRules.length == 1)
    assert(userReadSelfPasswordRules.head.ruleKind == Deny)
    assert {
      permissionTree
        .tree(Some("User"))("User")(Some("password"))(Read)(false)
        .isEmpty
    }
  }

  "Anyone" should "be able to read all `Todo` fields because of `allow READ Todo`" in {
    val singletonRule =
      permissionTree.tree(None)("Todo")(None)(Read)(false)
    assert(singletonRule.length == 1)
    assert(singletonRule.head.ruleKind == Allow)
    assert(singletonRule.head.resourcePath._1.id == "Todo")
    assert(!singletonRule.head.resourcePath._2.isDefined)
  }
}
