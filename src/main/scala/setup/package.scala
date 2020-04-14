package setup

import storage.Storage, schemaGenerator.ApiSchemaGenerator
import domain._
import Implicits._
import scala.util.Try
import setup.utils.DockerCompose

case class Setup(
    syntaxTree: SyntaxTree,
    storage: Storage
) {

  def setup(): Try[Unit] = Try {
    writeDockerComposeYaml(storage.dockerComposeYaml.get).get
    dockerComposeUp().get
    storage.migrate()
  }

  def dockerComposeUp() =
    "docker-compose -f ./.pragma/docker-compose.yml up -d" $
      "Error: Couldn't run docker-compose. Make sure docker and docker-compose are installed on your machine"

  def dockerComposeDown() =
    "docker-compose -f ./.pragma/docker-compose.yml down" $
      "Error: Couldn't run docker-compose. Make sure docker and docker-compose are installed on your machine"

  def writeDockerComposeYaml(dockerComposeFile: DockerCompose) =
    "mkdir .pragma" $ "Filesystem Error: Couldn't create .pragma directory"

  def buildApiSchema(): SyntaxTree =
    ApiSchemaGenerator(syntaxTree).buildApiSchemaAsSyntaxTree

}
