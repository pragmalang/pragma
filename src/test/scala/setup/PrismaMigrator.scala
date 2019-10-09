package tests.setup

import org.scalatest._
import setup.PrismaMigrator
import sangria.schema._
import sangria.parser.QueryParser

class PrismaMigratorTests extends WordSpec {
  "PrismaMigrator" should {
    "filter out all Query types" in {
      val renderedSchema = PrismaMigrator(
        Some(
          Schema
            .buildFromAst(QueryParser.parse("type Query { field: String }").get)
        )
      ).renderedSchema
      assert(renderedSchema == "")
    }

    "filter out all Mutation types" in {
      val renderedSchema = PrismaMigrator(
        Some(
          Schema.buildFromAst(
            QueryParser
              .parse(
                "type Query { field: String } type Mutation { field: String }"
              )
              .get
          )
        )
      ).renderedSchema
      assert(renderedSchema == "")
    }

    "filter out all Subscription types" in {
      val renderedSchema = PrismaMigrator(
        Some(
          Schema.buildFromAst(
            QueryParser
              .parse(
                "type Query { field: String } type Subscription { field: String }"
              )
              .get
          )
        )
      ).renderedSchema
      assert(renderedSchema == "")
    }
  }
}
