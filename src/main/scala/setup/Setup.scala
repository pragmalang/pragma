package setup

import domain._
import primitives._
import running.QueryExecutor

import sangria.ast._

import Implicits._
import java.io._
import scala.util.{Success, Failure, Try}

case class Setup(
    syntaxTree: SyntaxTree
) {

  val storage: Storage = PrismaMongo(syntaxTree)

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

  def build(): (Document, QueryExecutor) = (buildApiSchema, buildExecutor)

  def buildApiSchema(): Document = {
    val graphQlConverter = GraphQlConverter(syntaxTree)
    val builtins = GraphQlConverter.buitlinGraphQlTypeDefinitions
    val outputTypes = graphQlConverter.outputTypes
    val objectInputTypes =
      graphQlConverter.inputTypes(GraphQlConverter.ObjectInput)
    val referenceInputTypes =
      graphQlConverter.inputTypes(GraphQlConverter.ReferenceInput)
    val optionalInputTypes =
      graphQlConverter.inputTypes(GraphQlConverter.OptionalInput)
    val notificationTypes = graphQlConverter.notificationTypes

    val queryType: ObjectTypeDefinition = graphQlConverter.queryType
    val mutationType: ObjectTypeDefinition = ???
    val subscriptionType: ObjectTypeDefinition = ???

    Document(
      (queryType
        :: mutationType
        :: subscriptionType
        :: builtins
        ::: outputTypes
        ::: objectInputTypes
        ::: referenceInputTypes
        ::: optionalInputTypes
        ::: notificationTypes).toVector
    )
  }

  def buildExecutor(): QueryExecutor = QueryExecutor(syntaxTree, storage)
}
