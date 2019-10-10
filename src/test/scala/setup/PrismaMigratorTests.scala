package tests.setup

import org.scalatest._
import setup.PrismaMigrator
import sangria.schema._
import sangria.parser.QueryParser

class PrismaMigratorTests extends WordSpec {
  "PrismaMigrator" should {
    "filter out all Query types" in {
      val renderedSchema = PrismaMigrator(
        schemaOption =
          Some(QueryParser.parse("type Query { field: String }").get),
        prismaServerUri = "http://localhost:4466/test/dev"
      ).renderedSchema
      assert(renderedSchema == "")
    }

    "filter out all Mutation types" in {
      val renderedSchema = PrismaMigrator(
        schemaOption = Some(
          QueryParser
            .parse(
              "type Query { field: String } type Mutation { field: String }"
            )
            .get
        ),
        prismaServerUri = "http://localhost:4466/test/dev"
      ).renderedSchema
      assert(renderedSchema == "")
    }

    "filter out all Subscription types" in {
      val renderedSchema = PrismaMigrator(
        schemaOption = Some(
          QueryParser
            .parse(
              "type Query { field: String } type Subscription { field: String }"
            )
            .get
        ),
        prismaServerUri = "http://localhost:4466/test/dev"
      ).renderedSchema
      assert(renderedSchema == "")
    }
  }
}
