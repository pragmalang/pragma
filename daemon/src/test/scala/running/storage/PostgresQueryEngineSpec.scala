package running.storage.postgres

import pragma.domain._
import org.scalatest._
import doobie.implicits._
import spray.json._
import sangria.macros._
import running.operations._
import running.storage.postgres.instances._
import running.storage.TestStorage
import cats.implicits._
import org.scalatest.flatspec.AnyFlatSpec
import running.Request
import running.utils._
import running.TestUtils._

/** NOTE: These tests may fail if executed out of order
  * They also require a running Postgress instance
  */
class PostgresQueryEngineSpec extends AnyFlatSpec {
  val dkr = Tag("Docker")

  case class Country(
      code: String,
      name: String,
      population: Long,
      gnp: Option[Double]
  )

  val code = """
  @1 model Citizen {
    @1 name: String @primary
    @2 home: Country?
  }

  @2 model Country {
    @1 code: String @primary
    @2 name: String
    @3 population: Int = 0
    @4 gnp: Float?
    @5 citizens: [Citizen]
  }

  config { projectName = "test" }
  """

  implicit val syntaxTree = SyntaxTree.from(code).get
  implicit val opParser = new OperationParser(syntaxTree)
  val testStorage = new TestStorage(syntaxTree)
  import testStorage._

  migrationEngine.migrate(Mode.Dev, code).unsafeRunSync()

  sql"""
    INSERT INTO  "Country" ("code", "name", "population", "gnp") 
      VALUES ('SY', 'Syria', 6000, 10),
             ('US', 'America', 100000, 999999),
             ('JO', 'Jordan', 5505, 12343),
             ('SE', 'Sweden', 20, 300);
    
    INSERT INTO "Citizen" ("name") 
      VALUES ('John'),
             ('Ali');

    INSERT INTO "Country_citizens" ("source_Country", "target_Citizen")
      VALUES ('US', 'John'),
             ('SY', 'Ali');
  """.update.run.transact(t).unsafeRunSync()

  "Query engine" should "connect to the database and run queries" in {
    val countries =
      sql"""select "code", "name", "population", "gnp" from "Country";"""
        .query[Country]
        .to[List]
        .transact(t)
        .unsafeRunSync()

    assert(!countries.isEmpty)
  }

  "doobie.Read instance for JsObject" should "read all fields of a table" in {
    val sql = sql"""
    select * from "Country";
    """.query[JsObject]

    val results = sql.to[List].transact(t).unsafeRunSync()

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
        read(code: "US") {
          citizens {
            name
          }
        }
      }
    }
    """
    val req = Request.bareReqFrom(gqlQuery)
    val ops = opParser
      .parse(req)
      .getOrElse(fail("Ops should be constructed successfully"))

    val resultUS = queryEngine
      .readOneRecord(
        syntaxTree.modelsById("Country"),
        JsString("US"),
        ops(None)("Country").head.innerReadOps
      )
      .transact(t)
      .unsafeRunSync()

    val expectedUS = JsObject(
      Map(
        "code" -> JsString("US"),
        "citizens" -> JsArray(Vector(JsObject(Map("name" -> JsString("John")))))
      )
    )

    assert(resultUS === expectedUS)
  }

  "PostgresQueryEngine#readOneRecord" should "read all selected fields recursively" in {
    val gqlQuery = gql"""
    {
      Country {
        read(code: "US") {
          code
          name
        }
      }
    }
    """
    val req = Request.bareReqFrom(gqlQuery)
    val iops = opParser
      .parse(req)
      .getOrElse(fail("Ops should be constructed successfully"))
      .apply(None)("Country")
      .head
      .innerReadOps

    val us = queryEngine.readOneRecord(
      syntaxTree.modelsById("Country"),
      JsString("US"),
      iops
    )

    assert(us.transact(t).unsafeRunSync().fields("code") == JsString("US"))
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
    val req = Request.bareReqFrom(gqlQuery)
    val reqOps = opParser
      .parse(req)
      .getOrElse(fail("Ops should be constructed successfully"))

    val results = queryEngine
      .readManyRecords(
        syntaxTree.modelsById("Country"),
        ModelAgg(syntaxTree.modelsById("Country"), Nil, None, None, None),
        reqOps(None)("Country").head.innerReadOps
      )
      .transact(t)
      .unsafeRunSync()

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
            "code" -> JsString("US"),
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

  "PostgresQueryEngine#createOneRecord" should "insert a record into the database and handle default values" taggedAs (dkr) in {
    val gqlQuery = gql"""
    mutation {
      Country {
        create(country: {
          code: "JP",
          name: "Japan",
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
    val inserted = runGql(gqlQuery)
    val expected = Map(
      None -> Vector(
        JsObject(
          Map(
            "code" -> JsString("JP"),
            "name" -> JsString("Japan"),
            "gnp" -> JsNumber(9254.542),
            "population" -> JsNumber(0)
          )
        )
      )
    )

    assert(inserted === expected)
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
            { name: "John" }
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
    val result = runGql(gqlQuery)
    val expected = Map(
      Some("createDenmark") -> Vector(
        JsObject(
          Map(
            "gnp" -> JsNumber(9940934.542),
            "name" -> JsString("Denmark"),
            "population" -> JsNumber(682940),
            "citizens" -> JsArray(
              Vector(JsObject(Map("name" -> JsString("John"))))
            ),
            "code" -> JsString("DK")
          )
        )
      )
    )

    assert(result === expected)
  }

  "PostgresQueryEngine#createOneRecord" should "handle nested inserts correctly" taggedAs (dkr) in {
    val gqlQuery = gql"""
    mutation createJeff {
      Citizen {
        create(citizen: {
          name: "Jeff",
          home: {
            code: "CA",
            name: "Canada",
            population: 55050,
            citizens: []
          }
        }) {
          name
          home {
            code
            name
            population
          }
        }
      }
    }
    """
    val result = runGql(gqlQuery)
    val expected = Map(
      Some("createJeff") -> Vector(
        JsObject(
          Map(
            "name" -> JsString("Jeff"),
            "home" -> JsObject(
              Map(
                "code" -> JsString("CA"),
                "name" -> JsString("Canada"),
                "population" -> JsNumber(55050)
              )
            )
          )
        )
      )
    )

    assert(result === expected)
  }

  "PostgresQueryEngine#createOneRecord" should "handle referencing an existing record by ID" taggedAs (dkr) in {
    val gqlQuery = gql"""
    mutation createJackson {
      Citizen {
        create(citizen: {
          name: "Jackson",
          home: {
            code: "US" # US already exists. It's being referenced here
          }
        }) {
          name
          home {
            code
            name
            population
          }
        }
      }
    }
    """
    val result = runGql(gqlQuery)
    val expected = Map(
      Some("createJackson") -> Vector(
        JsObject(
          Map(
            "name" -> JsString("Jackson"),
            "home" -> JsObject(
              Map(
                "code" -> JsString("US"),
                "name" -> JsString("America"),
                "population" -> JsNumber(100000)
              )
            )
          )
        )
      )
    )

    assert(result === expected)
  }

  "PostgresQueryEngine#pushTo" should "push references and full objects to array fields" taggedAs (dkr) in {
    val gqlQuery = gql"""
    mutation {
      Country {
        pushToCitizens(code: "US", item: { name: "Jackson" }) {
          name
        }
      }
    }
    """
    val result = runGql(gqlQuery)
    val expected =
      Map(None -> Vector(JsObject(Map("name" -> JsString("Jackson")))))

    assert(result === expected)
  }

  "PostgresQueryEngine#deleteOneRecord" should "delete records successfully" taggedAs (dkr) in {
    val gqlQuery = gql"""
    mutation {
      Citizen {
        delete(name: "Jackson") {
          name
          home {
            code
          }
        }
      }
    }
    """
    val deleteResult = runGql(gqlQuery)
    val expectedDeleteResult = Map(
      None -> Vector(
        JsObject(
          Map(
            "name" -> JsString("Jackson"),
            "home" -> JsObject(Map("code" -> JsString("US")))
          )
        )
      )
    )

    assert(deleteResult === expectedDeleteResult)

    try {
      val readDeleted = gql"""
      query {
        Citizen {
          read(name: "Jackson") {
            name
          }
        }
      }
      """
      runGql(readDeleted)
      fail("Should not have been able to read the deleted record")
    } catch {
      case _: Exception => ()
    }
  }

  "PostgresQueryEngine#deleteOneFrom" should "successfully remove a single element from an array field" taggedAs (dkr) in {
    val gqlQuery = gql"""
    mutation {
      Country {
        removeFromCitizens(code: "US", item: "John") {
          name
        }

        read(code: "US") {
          citizens {
            name
          }
        }
      }
    }
    """
    val result = runGql(gqlQuery)
    val expected = Map(
      None -> Vector(
        JsObject(Map("name" -> JsString("John"))),
        // No "John" in `citizens`
        JsObject(Map("code" -> JsString("US"), "citizens" -> JsArray(Vector())))
      )
    )

    assert(result === expected)
  }

  "PostgresQueryEngine#updateOneRecord" should "successfully patch a given row" taggedAs (dkr) in {
    val gqlQuery = gql"""
    mutation {
      Country {
        update(code: "US", country: {
          name: "United States"
        }) {
          code
          name
        }
      }
    }
    """
    val updateResult = runGql(gqlQuery)
    val expected = Map(
      None -> Vector(
        JsObject(
          Map("code" -> JsString("US"), "name" -> JsString("United States"))
        )
      )
    )

    assert(updateResult === expected)
  }

  "Query aggregation" should "filter and take ranges of records correctly" in {
    val results = runGql {
      gql"""
      {
        Country {
          first3LargeCountries: list(aggregation: {
            filter: [
              { predicate: { population: { gt: 100 } } }
            ],
            from: 1,
            to: 3
          }) {
            name
          }
          withOneCitizen: list(aggregation: {
            filter: [
              { predicate: { citizens: { length: { eq: 1 } } } }
            ]
          }) {
            name
            citizens {
              name
            }
          }
        }
      }
      """
    }

    val expected = Map(
      None -> Vector(
        JsArray(
          Vector(
            JsObject(
              Map("code" -> JsString("SY"), "name" -> JsString("Syria"))
            ),
            JsObject(
              Map("code" -> JsString("JO"), "name" -> JsString("Jordan"))
            ),
            JsObject(
              Map("code" -> JsString("DK"), "name" -> JsString("Denmark"))
            )
          )
        ),
        JsArray(
          Vector(
            JsObject(
              Map(
                "code" -> JsString("SY"),
                "name" -> JsString("Syria"),
                "citizens" -> JsArray(
                  Vector(JsObject(Map("name" -> JsString("Ali"))))
                )
              )
            ),
            JsObject(
              Map(
                "code" -> JsString("DK"),
                "name" -> JsString("Denmark"),
                "citizens" -> JsArray(
                  Vector(JsObject(Map("name" -> JsString("John"))))
                )
              )
            )
          )
        )
      )
    )

    assert(results == expected)
  }

}
