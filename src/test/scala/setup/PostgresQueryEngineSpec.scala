package setup.storage.postgres

import domain.SyntaxTree
import setup.storage.postgres.PostgresQueryEngine
import org.scalatest._
import doobie._
import doobie.implicits._
import cats._
import PostgresQueryEngine._
import spray.json._
import cats.effect._
import sangria.macros._
import running.pipeline._
import setup.storage.QueryWhere
import scala.util._

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
  @1 model Citizen {
    @1 name: String @primary
  }

  @2 model Country {
    @1 code: String @primary
    @2 name: String
    @3 population: Int
    @4 gnp: Float?
    @5 citizens: [Citizen]
  }
  """

  implicit val syntaxTree = SyntaxTree.from(code).get
  val queryEngine = new PostgresQueryEngine(t, syntaxTree)
  val migrationEngine = new PostgresMigrationEngine[Id](syntaxTree)

  val initSql = migrationEngine.initialMigration.renderSQL(syntaxTree)

  Fragment(initSql, Nil, None).update.run
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
    
    INSERT INTO "Citizen" ("name") 
      VALUES ('John');
    
    INSERT INTO "Citizen" ("name") 
      VALUES ('Ali');

    INSERT INTO "Country_citizens" ("source_Country", "target_Citizen")
      VALUES ('USA', 'John');

    INSERT INTO "Country_citizens" ("source_Country", "target_Citizen")
      VALUES ('SY', 'Ali');
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

  "Array-field population" should "read array fields from join tables" taggedAs (dkr) in {
    val gqlQuery = gql"""
    {
      Country {
        read(code: "USA") {
          citizens {
            name
          }
        }
      }
    }
    """
    val req = Request(None, None, None, gqlQuery, Right(Nil), Map.empty, "", "")
    val ops = Operations.from(req)

    val resultUsa = queryEngine
      .readOneRecord(
        syntaxTree.modelsById("Country"),
        "USA",
        ops(None).head.innerReadOps
      )
      .transact(t)
      .unsafeRunSync

    val expectedUsa = JsObject(
      Map(
        "code" -> JsString("USA"),
        "citizens" -> JsArray(Vector(JsObject(Map("name" -> JsString("John")))))
      )
    )

    assert(resultUsa == expectedUsa)
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
        list {
          code
          name
          population
          citizens {
            name
          }
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
    val reqOps = Operations.from(req)

    val results = queryEngine
      .readManyRecords(
        syntaxTree.modelsById("Country"),
        QueryWhere(None, None, None),
        reqOps(None).head.innerReadOps
      )
      .transact(t)
      .unsafeRunSync

    val expectedRecords = JsArray(
      Vector(
        JsObject(
          Map(
            "code" -> JsString("SY"),
            "name" -> JsString("Syria"),
            "population" -> JsNumber(6000),
            "citizens" -> JsArray(
              Vector(JsObject(Map("name" -> JsString("Ali"))))
            )
          )
        ),
        JsObject(
          Map(
            "code" -> JsString("USA"),
            "name" -> JsString("America"),
            "population" -> JsNumber(100000),
            "citizens" -> JsArray(
              Vector(JsObject(Map("name" -> JsString("John"))))
            )
          )
        ),
        JsObject(
          Map(
            "code" -> JsString("JO"),
            "name" -> JsString("Jordan"),
            "population" -> JsNumber(5505),
            "citizens" -> JsArray(Vector())
          )
        ),
        JsObject(
          Map(
            "code" -> JsString("SE"),
            "name" -> JsString("Sweden"),
            "population" -> JsNumber(20),
            "citizens" -> JsArray(Vector())
          )
        )
      )
    )

    assert(results == expectedRecords)
  }

  "PostgresQueryEngine#createOneRecord" should "insert a record into the database" taggedAs (dkr) in {
    val gqlQuery = gql"""
    mutation {
      Country {
        create(country: {
          code: "JP",
          name: "Japan",
          population: 2523,
          gnp: 9254.542,
          citizens: []
        }) {
          code
          name
          gnp
          population
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
    val reqOps = Operations.from(req)

    val inserted = queryEngine.run(reqOps).unsafeRunSync
    val expected = JsObject(
      Map(
        "data" -> JsObject(
          Map(
            "code" -> JsString("JP"),
            "name" -> JsString("Japan"),
            "gnp" -> JsNumber(9254.542),
            "population" -> JsNumber(2523)
          )
        )
      )
    )

    assert(inserted == expected)
  }

  "PostgresQueryEngine#run" should "populate results of inserts correctly" taggedAs (dkr) in {
    val gqlQuery = gql"""
    mutation createDenmark {
      Country {
        create(country: {
          code: "DK",
          name: "Denmark",
          population: 682940,
          gnp: 9940934.542,
          citizens: [
            { name: "Jack" }
          ]
        }) {
          code
          name
          gnp
          population
          citizens {
            name
          }
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
    val reqOps = Operations.from(req)
    val result = queryEngine.run(reqOps).unsafeRunSync
    val expected = JsObject(
      Map(
        "createDenmark" -> JsObject(
          Map(
            "gnp" -> JsNumber(9940934.542),
            "name" -> JsString("Denmark"),
            "population" -> JsNumber(682940),
            "citizens" -> JsArray(
              Vector(JsObject(Map("name" -> JsString("Jack"))))
            ),
            "code" -> JsString("DK")
          )
        )
      )
    )

    assert(result == expected)
  }

}
