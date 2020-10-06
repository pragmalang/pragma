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
  val syntaxTree = SyntaxTree.from(schema).get

  val Project = syntaxTree.modelsById("Project")
  val Migration = syntaxTree.modelsById("Migration")
  val ImportedFile = syntaxTree.modelsById("ImportedFile")

  val jc = new JwtCodec("1234567")
  val queryEngine = new PostgresQueryEngine(transactor, syntaxTree, jc)
  val dummyFuncExecutor = new PFunctionExecutor[IO](
    WskConfig(
      1,
      "",
      Uri.fromString("http://localhost:6000").toTry.get,
      "2112ssdf"
    )
  )
  val migrationEngine = new PostgresMigrationEngine[IO](
    transactor,
    prevSyntaxTree,
    syntaxTree,
    queryEngine,
    dummyFuncExecutor
  )

  def migrate: IO[Unit] = {
    // val query =
    //   sql"""
    //     CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

    //     CREATE TABLE projects (
    //       name          TEXT       PRIMARY KEY,
    //       pg_uri        TEXT       NOT NULL,
    //       pg_user       TEXT       NOT NULL,
    //       pg_password   TEXT       NOT NULL,
    //       secret        TEXT       NOT NULL
    //     );

    //     CREATE TABLE migrations (
    //       id            uuid       DEFAULT uuid_generate_v4 () PRIMARY KEY,
    //       code          TEXT       NOT NULL,
    //       timestamp     TIMESTAMP  DEFAULT (now() at time zone 'utc')
    //     );

    //     CREATE TABLE imported_files (
    //       id            uuid       DEFAULT uuid_generate_v4 () PRIMARY KEY,
    //       content       TEXT       NOT NULL,
    //       file_name     TEXT       NOT NULL,
    //       migration_id  uuid        REFERENCES migrations(id)
    //     );

    //     ALTER TABLE projects
    //       ADD COLUMN
    //         current_migration uuid REFERENCES migrations(id);

    //     ALTER TABLE migrations
    //       ADD COLUMN project_name
    //         TEXT NOT NULL REFERENCES projects(name);
    //   """.update.run
    // query.transact(transactor).void
    migrationEngine.migrate
  }

  def runQuery[T](query: ConnectionIO[T]) = query.transact(transactor)

  def createProject(project: ProjectInput): ConnectionIO[Unit] = {

    // val insertProject = sql"""
    //       insert into
    //         projects (name, pg_user, pg_password, pg_uri)
    //         values (${project.name}, ${project.pgUser}, ${project.pgPassword}, ${project.pgUri});
    //     """.update.run.void

    // val insertMigrations = for {
    //   _ <- insertProject
    //   _ <- project.migrationHistory
    //     .traverse(m => createMigration(project.name, m))
    // } yield ()

    // insertMigrations

    val record = project.toJson.asJsObject
    queryEngine.createOneRecord(Project, record, Vector.empty).void
  }

  def getCurrentMigration(projectName: String) = {
    val query =
      sql"""
        SELECT
          *
        FROM projects p
        INNER JOIN migrations m ON $projectName = m.project_name
        INNER JOIN imported_files f ON f.migration_id = m.id;
        WHERE p.name = $projectName
      """.query[JsObject]

    print(query)
    ???
  }

  private def updateCurrentMigration(projectName: String, migrationId: UUID) =
    sql"""
      update projects set current_migration = $migrationId where name = $projectName;
    """.update.run.void

  def createMigration(
      projectName: String,
      migration: MigrationInput
  ): ConnectionIO[UUID] = {

    val insertMigration =
      sql"""
        insert into migrations (code, project_name) values (${migration.code}, $projectName) returning id;
      """.update.withUniqueGeneratedKeys[UUID]("id")

    val insertImportedFiles = for {
      migrationId <- insertMigration
      _ <- updateCurrentMigration(projectName, migrationId)
      _ <- migration.importedFiles
        .traverse(f => createImportedFile(migrationId, f))
    } yield migrationId

    insertImportedFiles
  }

  private def createImportedFile(
      migrationId: UUID,
      importedFile: ImportedFileInput
  ): ConnectionIO[String] =
    sql"""
      insert into imported_files (content, file_name, migration_id)
        values (${importedFile.content}, ${importedFile.fileName}, $migrationId) returning id;
    """.update.withUniqueGeneratedKeys("id")

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
