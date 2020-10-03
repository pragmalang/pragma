package setup.server

import doobie._
import doobie.implicits._
import cats.effect._
import cats.implicits._

class DaemonDB(transactor: Transactor[IO])(implicit cs: ContextShift[IO]) {

  def migrate: IO[Unit] = {
    val query =
      sql"""
        CREATE TABLE projects (
          id            SERIAL     PRIMARY KEY,
          name          TEXT,
          pg_uri        TEXT       NOT NULL,
          pg_user       TEXT       NOT NULL,
          pg_passhash   TEXT       NOT NULL,
        );

        CREATE TABLE migrations (
          id            SERIAL     PRIMARY KEY,
          code          TEXT       NOT NULL,
          timestamp     TIMESTAMP  DEFAULT (now() at time zone 'utc')
        );

        CREATE TABLE imported_files (
          id            SERIAL     PRIMARY KEY,
          content       TEXT       NOT NULL,
          file_name     TEXT       NOT NULL,
          migration_id  INT        REFERENCES migrations(id)
        );

        ALTER TABLE projects
          ADD COLUMN 
            current_migration INT REFERENCES migrations(id);

        ALTER TABLE migrations 
          ADD COLUMN project_id 
            INT NOT NULL REFERENCES projects(id); 
      """.update.run
    query.transact(transactor).void
  }

  def runQuery[T](query: ConnectionIO[T]) = query.transact(transactor)

  def createProject(project: ProjectInput): ConnectionIO[Int] = {

    val insertProject = sql"""
          insert into
            projects (name, pg_user, pg_passhash, pg_uri) 
            values (${project.name}, ${project.pgUser}, ${project.pgPassword}, ${project.pgUri})
            returning id;
        """.update.run

    val insertMigrations: ConnectionIO[Int] = for {
      projectId <- insertProject
      _ <- project.migrationHistory
        .map(m => createMigration(projectId, m))
        .sequence
    } yield projectId

    insertMigrations
  }

  private def updateCurrentMigration(projectId: Int, migrationId: Int) =
    sql"""
      update projects set current_migration = $migrationId where id = $projectId;
    """.update.run.void

  def createMigration(
      projectId: Int,
      migration: MigrationInput
  ): ConnectionIO[Int] = {

    val insertMigration =
      sql"""
        insert into migrations (code, project_id) values (${migration.code}, $projectId) returning id;
      """.update.run

    val insertImportedFiles = for {
      migrationId <- insertMigration
      _ <- updateCurrentMigration(projectId, migrationId)
      _ <- migration.importedFiles
        .map(f => createImportedFile(migrationId, f))
        .sequence
    } yield migrationId

    insertImportedFiles
  }

  private def createImportedFile(
      migrationId: Int,
      importedFile: ImportedFileInput
  ) =
    sql"""
      insert into imported_files (content, file_name, migration_id)
        values (${importedFile.content}, ${importedFile.fileName}, $migrationId) returning id;
    """.update.run

}

case class Migration(
    id: Int,
    code: String,
    migrationTimestamp: Long,
    importedFiles: List[ImportedFile]
)
case class MigrationInput(
    code: String,
    importedFiles: List[ImportedFileInput]
)

case class ImportedFile(id: Int, fileName: String, content: String)
case class ImportedFileInput(fileName: String, content: String)

case class Project(
    id: Int,
    pgUri: String,
    pgUser: String,
    pgPasshash: String,
    name: Option[String],
    currentMigration: Option[Migration],
    migrationHistory: List[Migration]
)
case class ProjectInput(
    name: Option[String],
    pgUri: String,
    pgUser: String,
    pgPassword: String,
    currentMigration: Option[MigrationInput],
    migrationHistory: List[MigrationInput]
)
