package parsing

import org.scalatest.FlatSpec
import parsing.substitution.Substitutor
import domain.{PImport, GraalFunction, SyntaxTree}
import spray.json._
import scala.util.Success
import org.graalvm.polyglot.Context
import domain._

class Substitution extends FlatSpec {

  "Substitutor" should "return an object containing all defined functions in a file as GraalFunctionValues using readGraalFunctions" in {
    val pImport =
      PImport("functions", "./src/test/scala/parsing/test-functions.js", None)
    val graalCtx = Context.create()
    val functionObject =
      Substitutor.readGraalFunctionsIntoContext(pImport, graalCtx).get
    val f = functionObject.value("f").asInstanceOf[GraalFunction]
    val additionResult = f.execute(JsNumber(2))
    assert(
      additionResult.get.asInstanceOf[JsNumber].value == BigDecimal(
        Success(3.0).value
      )
    )
    graalCtx.close()
  }

  "Substitutor" should "substitute function references in directives with actual functions" in {
    val code = """
    import "./src/test/scala/parsing/test-functions.js" as fns

    @1
    @onWrite(function: fns.validateCat)
    model Cat { @1 name: String }
    """
    val syntaxTree = SyntaxTree.from(code).get
    val substituted = Substitutor.substitute(syntaxTree).get
    val directive = substituted.models.head.directives.head
    directive.args.value("function") match {
      case GraalFunction(id, _, filePath, graalCtx, languageId) => {
        assert(id == "validateCat")
        assert(filePath == "./src/test/scala/parsing/test-functions.js")
      }
      case _ => fail("Result should be a Graal function 'validateCat'")
    }
  }

  "Substitutor" should "be able to retrieve referenced data from context" in {
    val code = """
    import "./src/test/scala/parsing/test-functions.js" as fns
    """
    val syntaxTree = SyntaxTree.from(code).get
    val graalCtx = Context.create()
    val ctx =
      Substitutor.getContext(syntaxTree.imports.toSeq, graalCtx).get
    val ref = PragmaParser
      .Reference(
        List("fns", "validateCat"),
        None
      )
    val retrieved = Substitutor.getReferencedFunction(ref, ctx.value)
    retrieved match {
      case Some(f: GraalFunction) => {
        assert(f.filePath == "./src/test/scala/parsing/test-functions.js")
        assert(f.id == "validateCat")
      }
      case None => fail("Should've found referenced value")
      case _    => ()
    }
    graalCtx.close()
  }

  "Predicate references in permissions" should "be substituted with actual predicates correctly" in {
    val code = """
    @1 @user model User {
      @1 username: String @publicCredential
      @2 password: String @secretCredential
      @3 todos: [Todo]
    }

    @2 model Todo { @1 title: String }

    import "./src/test/scala/parsing/test-functions.js" as fns

    role User {
      allow ALL Todo.title if fns.isOwner
    }
    """
    val syntaxTree = SyntaxTree.from(code).get
    val newGlobalTenant = syntaxTree.permissions.globalTenant
    val todoOwnershipPredicate =
      newGlobalTenant.roles.head.rules.head.predicate.get
        .asInstanceOf[GraalFunction]
    assert(todoOwnershipPredicate.id == "isOwner")
  }

  "Substitutor" should "substitute `self` references in access rules with appropriate model ref and predicate" in {
    val code = """
   @1 @user model User {
        @1 username: String @publicCredential
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

    assert(selfRule.ruleKind == Allow)
    assert(selfRule.resourcePath._1.id == "User")
    assert(selfRule.resourcePath._2.get.id == "bio")
    assert(selfRule.permissions == Set(Update))
    assert(selfRule.predicate.get.isInstanceOf[IfSelfAuthPredicate])
  }

}
