package setup.storage.postgres

import domain.SyntaxTree
import setup.storage.postgres.PostgresQueryEngine
import org.scalatest._
import doobie._
import doobie.implicits._
import cats._
import cats.effect._
import PostgresQueryEngine._
import spray.json._
import cats.effect._
import sangria.macros._
import running.pipeline._

class PostgresQueryEngineSpec extends FlatSpec {
  implicit val cs = IO.contextShift(ExecutionContexts.synchronous)

  val t = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver",
    "jdbc:postgresql://localhost:5433/test",
    "test",
    "test",
    Blocker.liftExecutionContext(ExecutionContexts.synchronous)
  )

  case class Country(
      code: String,
      name: String,
      population: Long,
      gnp: Option[Double]
  )

  val code = """
  @1 model Country {
    @1 code: String @primary
    @2 name: String
    @3 population: Int
    @4 gnp: Float?
  }
  """

  implicit val syntaxTree = SyntaxTree.from(code).get
  val queryEngine = new PostgresQueryEngine(t, syntaxTree)
  val migrationEngine = new PostgresMigrationEngine[Id](syntaxTree)

  Fragment(migrationEngine.initialMigration.renderSQL, Nil, None).update.run
    .transact(t)
    .unsafeRunSync

  sql"""
    INSERT INTO  "Country" ("code", "name", "population", "gnp") 
      VALUES ('SY', 'Syria', 6000, 10);

    INSERT INTO  "Country" ("code", "name", "population", "gnp") 
      VALUES ('USA', 'America', 100000, 9999);

    INSERT INTO  "Country" ("code", "name", "population", "gnp") 
      VALUES ('JO', 'Jordan', 5505, 12343);

    INSERT INTO  "Country" ("code", "name", "population", "gnp") 
      VALUES ('SE', 'Sweden', 20, 300);
  """.update.run.transact(t).unsafeRunSync

  "Query engine" should "connect to the database and run queries" in {
    val countries =
      sql"""select * from "Country";"""
        .query[Country]
        .to[List]
        .transact(t)
        .unsafeRunSync

    assert(!countries.isEmpty)
  }

  "doobie.Read instance for JsObject" should "read all fields of a table" in {
    val sql = sql"""
    select * from "Country";
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

  "PostgresQueryEngine#readOneRecord" should "read all selected fields recursively" in {
    val gqlQuery = gql"""
    {
      Country {
        read(code: "USA") {
          code
          name
        }
      }
    }
    """
    val req = Request(None, None, None, gqlQuery, Right(Nil), Map.empty, "", "")
    val iops = Operations.operationsFrom(req).apply(None).head.innerReadOps
    val us = queryEngine.readOneRecord(
      syntaxTree.modelsById("Country"),
      "USA",
      iops
    )
    assert(us.transact(t).unsafeRunSync.fields("code") == JsString("USA"))
  }

}
