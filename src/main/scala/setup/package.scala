package setup

import storage.Storage, schemaGenerator.ApiSchemaGenerator
import domain._
import Implicits._
import scala.util.Try
import setup.utils.DockerCompose
import domain.utils.UserError
import domain.primitives.PStringValue
// import com.mongodb.ConnectionString
// import setup.storage.MongoStorage
import scala.concurrent.Await
import scala.concurrent.duration.Duration
// import org.mongodb.scala.MongoClient

case class Setup(syntaxTree: SyntaxTree) {

  def setup(migrationSteps: Vector[MigrationStep]): Try[Unit] = Try {

    def supportedDbTypes(dbType: String, dbUrl: String): Storage =
      dbType match {
        // case "mongo" => {
        //   val url = new ConnectionString(dbUrl)
        //   MongoStorage(
        //     syntaxTree,
        //     url,
        //     MongoClient(dbUrl).getDatabase(url.getDatabase())
        //   )
        // }
        case _         => throw UserError("Unsupported database type")
      }

    val dbUrlIsNotSpecified =
      "Database URL is not specified. Try setting it in the `DB_URL` environment variable or in your project's configurations as `db_type`"
    val dbTypeIsNotSpecified =
      "Database type is not specified. Try setting it in the `DB_TYPE` environment variable or in your project's configurations as `db_url`"

    val dbUrl = syntaxTree.config
      .flatMap(_.getConfigEntry("db_url"))
      .map(_.value.asInstanceOf[PStringValue].value) match {
      case Some(url) => Some(url)
      case None      => sys.env.get("DB_URL")
    }

    val dbType = syntaxTree.config
      .flatMap(_.getConfigEntry("db_type"))
      .map(_.value.asInstanceOf[PStringValue].value) match {
      case Some(value) => Some(value)
      case None        => sys.env.get("DB_TYPE")
    }

    val storage = (dbType, dbUrl) match {
      case (None, None) =>
        throw UserError(dbTypeIsNotSpecified, dbUrlIsNotSpecified)
      case (Some(_), None) => throw UserError(dbUrlIsNotSpecified)
      case (None, Some(_)) => throw UserError(dbTypeIsNotSpecified)
      case (Some(dbType), Some(dbUrl)) =>
        supportedDbTypes(dbType, dbUrl)
    }

    Await.result(storage.migrate(migrationSteps), Duration.Inf)
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
