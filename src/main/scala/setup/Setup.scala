package setup

import domain._
import primitives._
import utils.{TypeMismatchException}
import Implicits._

import sangria.schema._
import sangria.parser.QueryParser
import sangria.ast.{Document}

import scala.util.{Success, Failure, Try}

import sys.process._
import scala.language.postfixOps
import java.io._
import sangria.ast.{ObjectTypeDefinition, NamedType, FieldDefinition}

case class Setup(
    syntaxTree: SyntaxTree,
    migrator: Migrator
) {

  def run() = {
    writeDockerComposeYaml()
    dockerComposeUp()
    migrate()
  }

  def dockerComposeUp() =
    "docker-compose -f ./.heavenly-x/docker-compose.yml up -d" ! match {
      case 1 =>
        Failure(
          new Exception(
            "Error: Couldn't run docker-compose. Make sure docker and docker-compose are installed on your machine"
          )
        )
      case 0 => Success(())
    }

  def dockerComposeDown() =
    "docker-compose -f ./.heavenly-x/docker-compose.yml down" ! match {
      case 1 =>
        Failure(
          new Exception(
            "Error: Couldn't run docker-compose. Make sure docker and docker-compose are installed on your machine"
          )
        )
      case 0 => Success(())
    }

  def writeDockerComposeYaml() = {
    "mkdir .heavenly-x" ! match {
      case 1 =>
        throw new Exception(
          "Filesystem Error: Couldn't create .heavenlyx directory"
        )
      case 0 => {
        val pw = new PrintWriter(new File(".heavenly-x/docker-compose.yml"))
        pw.write(dockerComposeYaml())
        pw.close
      }
    }
  }

  def dockerComposeYaml(): String = Setup.defaultDockerComposeYaml

  def migrate(): Try[Unit] =
    migrator.schema(graphQlSchema(syntaxTree)).run

  def graphQlSchema(
      syntaxTree: SyntaxTree,
      queryType: ObjectTypeDefinition = ObjectTypeDefinition(
        name = "Query",
        interfaces = Vector.empty,
        fields = Vector(FieldDefinition("stub", NamedType("String"), Vector.empty))
      ),
      mutationType: Option[ObjectTypeDefinition] = None,
      subscriptionType: Option[ObjectTypeDefinition] = None
  ): Document =
    GraphQlDefinitionsIR(syntaxTree).buildGraphQLSchemaAst(
      query = queryType,
      mutation = mutationType,
      subscription = subscriptionType
    )

  def apiSchema(syntaxTree: SyntaxTree): Document = ???

  def executor(schema: Schema[Any, Any]): String => Any = (query: String) => ???
}

object Setup {
  def defaultDockerComposeYaml() =
  """
version: '3'
services:
  prisma:
    image: prismagraphql/prisma:1.34
    restart: always
    ports:
      - '4466:4466'
    environment:
      PRISMA_CONFIG: |
        port: 4466
        managementApiSecret:
        databases:
          default:
            connector: mongo
            uri: mongodb://prisma:prisma@mongo-db
  mongo-db:
    image: mongo:3.6
    restart: always
    environment:
      MONGO_INITDB_ROOT_USERNAME: prisma
      MONGO_INITDB_ROOT_PASSWORD: prisma
    ports:
      - '27017:27017'
    volumes:
      - mongo:/var/lib/mongo
volumes:
  mongo: ~
    """;
}
