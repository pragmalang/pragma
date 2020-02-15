package setup

import storage.Storage, schemaGenerator.ApiSchemaGenerator
import domain._
import running.execution.QueryExecutor
import Implicits._
import scala.util.Try

case class Setup(
    syntaxTree: SyntaxTree,
    storage: Storage
) {

  def setup(): Try[Unit] = Try {
    writeDockerComposeYaml().get
    dockerComposeUp().get
    storage.migrate().get
  }

  def dockerComposeUp() =
    "docker-compose -f ./.heavenly-x/docker-compose.yml up -d" $
      "Error: Couldn't run docker-compose. Make sure docker and docker-compose are installed on your machine"

  def dockerComposeDown() =
    "docker-compose -f ./.heavenly-x/docker-compose.yml down" $
      "Error: Couldn't run docker-compose. Make sure docker and docker-compose are installed on your machine"

  def writeDockerComposeYaml() =
    "mkdir .heavenly-x" $ "Filesystem Error: Couldn't create .heavenlyx directory"

  def build(): (SyntaxTree, QueryExecutor) = (buildApiSchema, buildExecutor)

  def buildApiSchema(): SyntaxTree =
    ApiSchemaGenerator.default(syntaxTree).buildApiSchemaAsSyntaxTree

  def buildExecutor(): QueryExecutor = QueryExecutor(syntaxTree, storage)
}
