package setup.server

import doobie._
import doobie.implicits._
import cats.effect._
import cats.implicits._
import java.util.UUID
import doobie.postgres.implicits._
import spray.json._
import running.storage.postgres.instances.jsObjectRead
import pragma.domain.SyntaxTree
import scala.util.Failure
import scala.util.Success
import running.storage.postgres.PostgresQueryEngine
import running.JwtCodec
import running.PFunctionExecutor
import running.WskConfig
import org.http4s.Uri
import running.storage.postgres.PostgresMigrationEngine
import setup.server.DaemonJsonProtocol._
import running.operations.InnerOperation
import pragma.domain.PModel
import running.operations.InnerReadOperation
import running.operations.Operations.AliasedField
import running.operations.OperationParser
import sangria.parser.QueryParser
import pragma.domain.utils.InternalException

class DaemonDB(transactor: Transactor[IO])(implicit cs: ContextShift[IO]) {

  val schema = """
  @1 model Project {
    @1 name: String @primary
    @2 secret: String
    @3 pgUri: String
    @4 pgUser: String
    @5 pgPassword: String
    @6 currentMigration: Migration?
    @7 migrationHistory: [Migration]
  }

  @2 model Migration {
    @1 id: String @primary @uuid
    @2 code: String
    @3 migrationTimestamp: Long
    @4 importedFiles: [ImportedFile]
  }

  @3 model ImportedFile {
    @1 id: String @primary @uuid
    @2 fileName: String
    @3 content: String
  }
  """

  val prevSyntaxTree = SyntaxTree.empty
  implicit val syntaxTree = SyntaxTree.from(schema).get

  val ProjectModel = syntaxTree.modelsById("Project")
  val MigrationModel = syntaxTree.modelsById("Migration")
  val ImportedFileModel = syntaxTree.modelsById("ImportedFile")

  val jc = new JwtCodec("1234567")
  val queryEngine = new PostgresQueryEngine(transactor, syntaxTree, jc)
  val funcExecutor = PFunctionExecutor.dummy[IO]
  val migrationEngine = new PostgresMigrationEngine[IO](
    transactor,
    prevSyntaxTree,
    syntaxTree,
    queryEngine,
    funcExecutor
  )
  val opParser = new OperationParser(syntaxTree)

  def migrate: IO[Unit] = migrationEngine.migrate

  def runQuery[T](query: ConnectionIO[T]) = query.transact(transactor)

  def createProject(project: ProjectInput): IO[Unit] = {
    val record = project.toJson.asJsObject
    queryEngine
      .createOneRecord(ProjectModel, record, Vector.empty)
      .void
      .transact(transactor)
  }

  def getProject(
      projectName: String
  ): IO[Option[Project]] = {
    val currentMigrationQuery = opParser
      .parse {
        running.Request.bareReqFrom {
          QueryParser.parse {
            s"""
              {
                Project {
                  read(name: $projectName) {
                    name
                    secret
                    pgUri
                    pgUser
                    pgPassword
                    migrationHistory {
                      id
                      migrationTimestamp
                      code
                      importedFiles {
                        id
                        fileName
                        content
                      }
                    }
                    currentMigration {
                      id
                      migrationTimestamp
                      code
                      importedFiles {
                        id
                        fileName
                        content
                      }
                    }
                  }
                }
              }
             """
          }.get
        }
      }
      .getOrElse {
        throw new InternalException("Invalid hard-coded query to fetch project")
      }

    queryEngine.run(currentMigrationQuery).map { query =>
      query.head._2.toMap
        .apply("Project")
        .head
        ._2
        .convertTo[Option[Project]]
    }
  }

}

case class Migration(
    id: String,
    code: String,
    migrationTimestamp: Long,
    importedFiles: List[ImportedFile]
)
case class MigrationInput(
    code: String,
    importedFiles: List[ImportedFileInput]
)

case class ImportedFile(id: String, fileName: String, content: String)
case class ImportedFileInput(fileName: String, content: String)

case class Project(
    name: String,
    secret: String,
    pgUri: String,
    pgUser: String,
    pgPassword: String,
    currentMigration: Option[Migration],
    migrationHistory: List[Migration]
)
case class ProjectInput(
    name: String,
    secret: String,
    pgUri: String,
    pgUser: String,
    pgPassword: String,
    currentMigration: Option[MigrationInput],
    migrationHistory: List[MigrationInput]
)

object DaemonDB {

  trait ImportedFileT
  case class ImportedFile(fileName: String, content: String)
      extends ImportedFileT
  case class ImportedFileAuto(id: String, file: ImportedFile)
      extends ImportedFileT

  case class NewMigration[A <: ImportedFileT](
      code: String,
      importedFiles: List[A]
  )
  trait MigrationT
  case class Migration(migration: NewMigration[ImportedFile]) extends MigrationT
  case class MigrationAuto(
      id: String,
      timestamp: Long,
      migration: NewMigration[ImportedFileAuto]
  ) extends MigrationT

  case class NewProject[M <: MigrationT](
      name: String,
      secret: String,
      pgUri: String,
      pgUser: String,
      pgPassword: String,
      currentMigration: Option[M],
      migrationHistory: List[M]
  )
  trait ProjectT
  case class Project(project: NewProject[Migration]) extends ProjectT
  case class ProjectAuto(project: NewProject[MigrationAuto]) extends ProjectT
}
