package running.storage

import pragma.domain._, running._
import org.scalatest.flatspec.AnyFlatSpec
import running.operations.OperationParser
import sangria.macros._
import running.operations.ReadManyOperation

class AggSqlGenSpec extends AnyFlatSpec {
  val code = """
  config { projectName = "test" }

  @1 model Book {
    @1 title: String @primary
    @2 price: Float
    @3 authors: [Author]
  }

  @2 @user model Author {
    @1 name: String @primary @publicCredential
    @2 password: String @secretCredential
  }
  """

  val syntaxTree = SyntaxTree.from(code).get
  val opParser = new OperationParser(syntaxTree)

  "Model-level ordering SQL" should "be generated correctly" in {
    val req = Request.bareReqFrom {
      gql"""
      {
        Book {
          list(aggregation: {
            orderBy: { field: "price", order: DESCENDING }
          }) {
            title
          }
        }
      }
      """
    }
    val listOp =
      opParser
        .parse(req)
        .getOrElse(fail("Query should be valid"))(None)("Book")
        .head
        .asInstanceOf[ReadManyOperation]

    val (sql, _, _, usedTables) = QueryAggSqlGen.modelAggSql(listOp.opArguments.agg)
    assert(sql === " ORDER BY \"Book\".\"price\" DESC ")
    assert(usedTables === Set("\"Book\""))
  }
}
