import doobie.implicits._
import cats.effect._
import cats.implicits._
import spray.json._
import pragma.domain.SyntaxTree, pragma.domain.DomainImplicits._
import running.storage.postgres.PostgresQueryEngine
import pragma.jwtUtils._, pragma.utils.JsonCodec._
import running.PFunctionExecutor
import running.storage.postgres.PostgresMigrationEngine
import running.operations.OperationParser
import sangria.parser.QueryParser
import pragma.domain.utils.InternalException
import running.RequestReducer
import pragma.daemonProtocol._
import running.utils.Mode
import doobie.hikari._
import running.utils.Mode.Dev
import running.utils.Mode.Prod

class DaemonDB(transactor: HikariTransactor[IO])(
    implicit cs: ContextShift[IO]
) {

  val schema = """
  config {
    projectName = "daemon_db"
  }

  @1 model Project {
    @1 name: String @primary
    @2 secret: String?
    @3 pgUri: String?
    @4 pgUser: String?
    @5 pgPassword: String?
    @6 previousDevMigration: Migration?
    @7 previousProdMigration: Migration?
  }

  @2 model Migration {
    @1 id: String @primary @uuid
    @2 code: String
    @3 functions: [ImportedFunction]
  }

  @3 model ImportedFunction {
    @1 id: String @primary @uuid
    @2 name: String
    @3 content: String
    @4 runtime: String
    @5 binary: Boolean
    @6 scopeName: String
  }
  """

  val syntaxTree = SyntaxTree.from(schema).get

  val ProjectModel = syntaxTree.modelsById("Project")
  val MigrationModel = syntaxTree.modelsById("Migration")
  val ImportedFunctionModel = syntaxTree.modelsById("ImportedFunction")

  val jc = new JwtCodec("1234567")
  val queryEngine = new PostgresQueryEngine(transactor, syntaxTree, jc)
  val funcExecutor = PFunctionExecutor.dummy[IO]
  val migrationEngine =
    new PostgresMigrationEngine[IO](
      transactor,
      syntaxTree,
      queryEngine,
      funcExecutor
    )

  val opParser = new OperationParser(syntaxTree)

  def migrate: IO[Unit] = migrationEngine.migrate(Mode.Prod, schema)

  def createProject(project: ProjectInput): IO[Unit] = {
    val record = project.toJson.asJsObject

    queryEngine
      .createOneRecord(ProjectModel, record, Vector.empty)
      .void
      .transact(transactor)
  }

  def deleteProject(projectName: String): IO[Unit] =
    queryEngine
      .deleteOneRecord(
        model = ProjectModel,
        primaryKeyValue = projectName.toJson,
        innerReadOps = Vector.empty,
        cascade = true
      )
      .void
      .transact(transactor)

  def getProject(
      projectName: String
  ): IO[Option[Project]] = {
    val projectQuery = opParser
      .parse {
        running.Request.bareReqFrom {
          QueryParser.parse {
            s"""
              {
                Project {
                  read(name: ${projectName.withQuotes}) {
                    name
                    secret
                    pgUri
                    pgUser
                    pgPassword
                    previousDevMigration {
                      id
                      code
                      functions {
                        id
                        name
                        content
                        runtime
                        binary
                      }
                    }
                    previousProdMigration {
                      id
                      code
                      functions {
                        id
                        name
                        content
                        runtime
                        binary
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

    queryEngine.run(projectQuery).map { query =>
      query.head._2.toMap
        .apply("Project")
        .head
        ._2
        .convertTo[Option[Project]]
    }
  }

  def persistPreviousMigration(
      projectName: String,
      previousMigration: MigrationInput,
      mode: Mode
  ): IO[Unit] = {
    val queryVars = JsObject(
      "projectName" -> projectName.toJson,
      "project" -> {
        mode match {
          case Dev =>
            JsObject("previousDevMigration" -> previousMigration.toJson)
          case Prod =>
            JsObject("previousProdMigration" -> previousMigration.toJson)
        }
      }
    )
    val gqlMut = QueryParser.parse {
      s"""
      mutation UpdatePrevious($$projectName: String!, $$project: ProjectInput!) {
        Project {
          update(name: $$projectName, project: $$project) {
            name
          }
        }
      }
      """
    }.get
    val mut =
      RequestReducer(
        running.Request.bareReqFrom(gqlMut).copy(queryVariables = queryVars)
      )

    val ops = opParser
      .parse(mut)
      .getOrElse {
        throw new InternalException("Invalid hard-coded query to fetch project")
      }

    queryEngine.run(ops).void
  }

}

object DaemonDB {

  sealed trait ImportedFileT
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
