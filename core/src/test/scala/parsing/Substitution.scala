package pragma.parsing

import pragma.domain._
import pragma.parsing.substitution.Substitutor
import spray.json._
import scala.util.Success
import org.scalatest.flatspec.AnyFlatSpec

class Substitution extends AnyFlatSpec {

  "Substitutor" should "substitute function references in directives with external functions" in {
    val code = """
    import "./core/src/test/scala/parsing/test-functions.js" as fns

    @1
    @onWrite(function: fns.validateCat)
    model Cat { @1 name: String @primary }
    """
    val syntaxTree = SyntaxTree.from(code).get
    val substituted = Substitutor.substitute(syntaxTree).get
    val directive = substituted.models.head.directives.head
    directive.args.value("function") match {
      case ExternalFunction(id, filePath) => {
        assert(id == "validateCat")
        assert(filePath == "./core/src/test/scala/parsing/test-functions.js")
      }
      case _ => fail("Result should be a Graal function 'validateCat'")
    }
  }

  "Predicate references in permissions" should "be substituted with actual predicates correctly" in {
    val code = """
    @1 @user model User {
      @1 username: String @publicCredential @primary
      @2 password: String @secretCredential
      @3 todos: [Todo]
    }

    @2 model Todo { @1 title: String @primary }

    import "./core/src/test/scala/parsing/test-functions.js" as fns

    role User {
      allow ALL Todo.title if fns.isOwner
    }
    """
    val syntaxTree = SyntaxTree.from(code).get
    val newGlobalTenant = syntaxTree.permissions.globalTenant
    val todoOwnershipPredicate =
      newGlobalTenant.roles.head.rules.head.predicate.get
        .asInstanceOf[ExternalFunction]
    assert(todoOwnershipPredicate.id == "isOwner")
  }

  "Substitutor" should "substitute `self` references in access rules with appropriate model ref and predicate" in {
    val code = """
   @1 @user model User {
        @1 username: String @publicCredential @primary
        @2 password: String @secretCredential
        @3 bio: String
      }

      role User {
        allow UPDATE self.bio
      }
    """
    val st = SyntaxTree.from(code).get
    val permissions = st.permissions
    val selfRule = permissions.globalTenant.roles.head.rules.head

    assert(selfRule.isSlefRule)
    assert(selfRule.ruleKind == Allow)
    assert(selfRule.resourcePath._1.id == "User")
    assert(selfRule.resourcePath._2.get.id == "bio")
    assert(selfRule.permissions == Set(Update))
  }

}
