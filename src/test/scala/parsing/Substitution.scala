package parsing

import org.scalatest.FlatSpec
import parsing.Substitutor
import domain.{PImport, GraalFunction, SyntaxTree}
import spray.json._
import scala.util.Success
import org.graalvm.polyglot.Context
import domain._, primitives._
import collection.immutable.ListMap
import org.parboiled2.Position

class Substitution extends FlatSpec {

  "Substitutor" should "return an object containing all defined functions in a file as GraalFunctionValues using readGraalFunctions" in {
    val Pimport =
      PImport("functions", "./src/test/scala/parsing/test-functions.js", None)
    val graalCtx = Context.create()
    val functionObject =
      Substitutor.readGraalFunctionsIntoContext(Pimport, graalCtx).get
    val f = functionObject.value("f").asInstanceOf[GraalFunction]
    val additionResult = f.execute(JsNumber(2))
    assert(
      additionResult.get.asInstanceOf[JsNumber].value == BigDecimal(
        Success(3.0).value
      )
    )
  }

  "Substitutor" should "substitute function references in directives with actual functions" in {
    val code = """
    import "./src/test/scala/parsing/test-functions.js" as fns

    @onWrite(function: fns.validateCat)
    model Cat { name: String }
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
    val ctx = Substitutor.getContext(syntaxTree.imports, graalCtx).get
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
  }

  "Predicate references in permissions" should "be substituted with actual predicates correctly" in {
    val code = """
    @user model User {
      username: String @publicCredential
      password: String @secretCredential
      todos: [Todo]
    }

    model Todo { title: String }

    import "./src/test/scala/parsing/test-functions.js" as fns

    role User {
      allow ALL Todo.title if fns.isOwner
    }
    """
    val syntaxTree = SyntaxTree.from(code).get
    val newGlobalTenant = syntaxTree.permissions.get.globalTenant
    val todoOwnershipPredicate =
      newGlobalTenant.roles.head.rules.head.predicate.get
        .asInstanceOf[GraalFunction]
    assert(todoOwnershipPredicate.id == "isOwner")
  }

  "Substitutor" should "substitute `self` references in access rules with appropriate model ref and predicate" in {
    val code = """
      @user model User {
        username: String @publicCredential
        password: String @secretCredential
        bio: String
      }

      role User {
        allow UPDATE self.bio
      }
    """
    val st = SyntaxTree.from(code).get
    val permissions = st.permissions.get
    val selfRule = permissions.globalTenant.roles.head.rules.head
    val expectedSelfRule = AccessRule(
      Allow,
      (
        PModel(
          "User",
          List(
            PModelField(
              "username",
              PString,
              None,
              List(
                Directive(
                  "publicCredential",
                  PInterfaceValue(ListMap(), PInterface("", List(), None)),
                  FieldDirective,
                  Some(PositionRange(Position(51, 3, 26), Position(68, 3, 43)))
                )
              ),
              Some(PositionRange(Position(34, 3, 9), Position(42, 3, 17)))
            ),
            PModelField(
              "password",
              PString,
              None,
              List(
                Directive(
                  "secretCredential",
                  PInterfaceValue(ListMap(), PInterface("", List(), None)),
                  FieldDirective,
                  Some(PositionRange(Position(94, 4, 26), Position(111, 4, 43)))
                )
              ),
              Some(PositionRange(Position(77, 4, 9), Position(85, 4, 17)))
            ),
            PModelField(
              "bio",
              PString,
              None,
              List(),
              Some(PositionRange(Position(120, 5, 9), Position(123, 5, 12)))
            )
          ),
          List(
            Directive(
              "user",
              PInterfaceValue(ListMap(), PInterface("", List(), None)),
              ModelDirective,
              Some(PositionRange(Position(7, 2, 7), Position(12, 2, 12)))
            )
          ),
          Some(PositionRange(Position(19, 2, 19), Position(23, 2, 23)))
        ),
        Some(
          PModelField(
            "bio",
            PString,
            None,
            List(),
            Some(PositionRange(Position(120, 5, 9), Position(123, 5, 12)))
          )
        )
      ),
      List(Update),
      Some(
        IfSelfAuthPredicate(
          PModel(
            "User",
            List(
              PModelField(
                "username",
                PString,
                None,
                List(
                  Directive(
                    "publicCredential",
                    PInterfaceValue(ListMap(), PInterface("", List(), None)),
                    FieldDirective,
                    Some(
                      PositionRange(Position(51, 3, 26), Position(68, 3, 43))
                    )
                  )
                ),
                Some(PositionRange(Position(34, 3, 9), Position(42, 3, 17)))
              ),
              PModelField(
                "password",
                PString,
                None,
                List(
                  Directive(
                    "secretCredential",
                    PInterfaceValue(ListMap(), PInterface("", List(), None)),
                    FieldDirective,
                    Some(
                      PositionRange(Position(94, 4, 26), Position(111, 4, 43))
                    )
                  )
                ),
                Some(PositionRange(Position(77, 4, 9), Position(85, 4, 17)))
              ),
              PModelField(
                "bio",
                PString,
                None,
                List(),
                Some(PositionRange(Position(120, 5, 9), Position(123, 5, 12)))
              )
            ),
            List(
              Directive(
                "user",
                PInterfaceValue(ListMap(), PInterface("", List(), None)),
                ModelDirective,
                Some(PositionRange(Position(7, 2, 7), Position(12, 2, 12)))
              )
            ),
            Some(PositionRange(Position(19, 2, 19), Position(23, 2, 23)))
          )
        )
      ),
      Some(PositionRange(Position(167, 9, 9), Position(188, 9, 30)))
    )

    assert(selfRule == expectedSelfRule)
  }

}
