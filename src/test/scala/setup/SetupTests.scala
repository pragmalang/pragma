package tests.setup

import org.scalatest._
import setup._
import domain._
import sangria.schema._
import sangria.parser.QueryParser

class SetupTests extends WordSpec {
  "Setup" should {
    "Create .heavenly-x/docker-compose.yml" in {
      // Setup(
      //   SyntaxTree(Nil, Nil, Nil, Nil, Permissions(Nil, None)),
      //   PrismaMigrator(
      //     Some(
      //       Schema.buildFromAst(
      //         QueryParser
      //           .parse(
      //             "type Query { field: String } type Subscription { field: String }"
      //           )
      //           .get
      //       )
      //     )
      //   )
      // ).writeDockerComposeYaml()
    }

    "Overwrite .heavenly-x/docker-compose.yml if any exists" in {
    }
  }
}
