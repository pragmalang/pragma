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
import setup.storage.QueryWhere

class PostgresQueryEngineSpec extends FlatSpec {
  val dkr = Tag("Docker")

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

  @2 model Citizen {
    @1 name: String @primary
    @2 country: Country
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
      VALUES ('USA', 'America', 100000, 999999);

    INSERT INTO  "Country" ("code", "name", "population", "gnp") 
      VALUES ('JO', 'Jordan', 5505, 12343);

    INSERT INTO  "Country" ("code", "name", "population", "gnp") 
      VALUES ('SE', 'Sweden', 20, 300);
    
    INSERT INTO "Citizen" ("name", "country") 
      VALUES ('John', 'USA');
    
    INSERT INTO "Citizen" ("name", "country") 
      VALUES ('Ali', 'SY')
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

  "Object population" should "replace foreign keys with object values" taggedAs (dkr) in {
    val gqlQuery = gql"""
    {
      Citizen {
        read(name: "John") {
          country {
            name
            population
          }
        }
      }
    }
    """
    val req = Request(None, None, None, gqlQuery, Right(Nil), Map.empty, "", "")
    val ops = Operations.from(req)

    val result = queryEngine
      .readOneRecord(
        syntaxTree.modelsById("Citizen"),
        "John",
        ops(None).head.innerReadOps
      )
      .transact(t)
      .unsafeRunSync

    val expected = JsObject(
      Map(
        "country" -> JsObject(
          Map("name" -> JsString("America"), "population" -> JsNumber(100000))
        )
      )
    )

    assert(result == expected)
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
    val iops = Operations.from(req).apply(None).head.innerReadOps
    val us = queryEngine.readOneRecord(
      syntaxTree.modelsById("Country"),
      "USA",
      iops
    )

    assert(us.transact(t).unsafeRunSync.fields("code") == JsString("USA"))
  }

  "PostgresQueryEngine#readMany" should "return the expected records" taggedAs (dkr) in {
    val gqlQuery = gql"""
    {
      Country {
        list(where: {}) {
          code
          name
        }
      }
    }
    """
    val req =
      Request(
        hookData = None,
        body = None,
        user = None,
        query = gqlQuery,
        queryVariables = Left(JsObject.empty),
        cookies = Map.empty,
        url = "",
        hostname = ""
      )
    val innerOps = Operations.from(req)

    val results = queryEngine
      .readManyRecords(
        syntaxTree.modelsById("Country"),
        QueryWhere(None, None, None),
        innerOps(None).head.innerReadOps
      )
      .transact(t)
      .unsafeRunSync

    val expected = JsArray(
      Vector(
        JsObject(Map("code" -> JsString("SY"), "name" -> JsString("Syria"))),
        JsObject(Map("code" -> JsString("USA"), "name" -> JsString("America"))),
        JsObject(Map("code" -> JsString("JO"), "name" -> JsString("Jordan"))),
        JsObject(Map("code" -> JsString("SE"), "name" -> JsString("Sweden")))
      )
    )

    assert(results == expected)
  }

}
