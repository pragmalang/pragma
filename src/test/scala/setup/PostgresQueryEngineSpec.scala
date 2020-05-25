package setup.storage

import domain.SyntaxTree
import setup.storage.postgres.PostgresQueryEngine
import org.scalatest._
import doobie._
import doobie.implicits._
import cats.effect._
import PostgresQueryEngine._
import spray.json._

class PostgresQueryEngineSpec extends FlatSpec {
  implicit val cs = IO.contextShift(ExecutionContexts.synchronous)

  val t = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver",
    "jdbc:postgresql://test-postgres-do-user-6445746-0.a.db.ondigitalocean.com:25060/defaultdb",
    "doadmin",
    "j85b8frfhy1ja163",
    Blocker.liftExecutionContext(ExecutionContexts.synchronous)
  )

  case class Country(
      code: String,
      name: String,
      population: Long,
      gnp: Option[Double]
  )

  val code = """
  model Country {
    code: String
    name: String
    population: Int
    gnp: Float?
  }
  """

  val syntaxTree = SyntaxTree.from(code).get
  val queryEngine = new PostgresQueryEngine(t, syntaxTree)

  "Query engine" should "connect to the database and run queries" in {
    val countries =
      sql"select * from country;"
        .query[Country]
        .to[List]
        .transact(t)
        .unsafeRunSync

    assert(!countries.isEmpty)
  }

  "doobie.Read instances for models" should "read all non-relational fields of the model" in {
    val sql = sql"""
    select * from country
    """.query[JsObject]

    val results = sql.to[List].transact(t).unsafeRunSync

    for (country <- results) {
      assert(country.fields("code").isInstanceOf[JsString])
      assert(country.fields("name").isInstanceOf[JsString])
      assert(country.fields("population").isInstanceOf[JsNumber])
      val gnp = country.fields("gnp")
      assert(gnp == JsNull || gnp.isInstanceOf[JsNumber])
    }
  }

}
