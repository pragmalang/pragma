import doobie._
import doobie.implicits._
import cats.effect._
import cats.implicits._
import spray.json._
import pragma.domain.SyntaxTree, pragma.domain.DomainImplicits._
import running.storage.postgres.PostgresQueryEngine
import running.JwtCodec
import running.PFunctionExecutor
import running.storage.postgres.PostgresMigrationEngine
import running.operations.OperationParser
import sangria.parser.QueryParser
import pragma.domain.utils.InternalException
import running.RequestReducer
import pragma.daemonProtocol._, DaemonJsonProtocol._

class DaemonDB(transactor: Resource[IO, Transactor[IO]])(
    implicit cs: ContextShift[IO]
) {

  val schema = """
  config {
    projectName = "daemon-db"
  }

  @1 model Project {
    @1 name: String @primary
    @2 secret: String
    @3 pgUri: String
    @4 pgUser: String
    @5 pgPassword: String
    @6 previousMigration: Migration?
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
  }
  """

  val prevSyntaxTree = SyntaxTree.empty
  val syntaxTree = SyntaxTree.from(schema).get

  val ProjectModel = syntaxTree.modelsById("Project")
  val MigrationModel = syntaxTree.modelsById("Migration")
  val ImportedFunctionModel = syntaxTree.modelsById("ImportedFunction")

  val jc = new JwtCodec("1234567")
  val queryEngine = transactor.map(new PostgresQueryEngine(_, syntaxTree, jc))
  val funcExecutor = PFunctionExecutor.dummy[IO]
  val migrationEngine = for {
    t <- transactor
    qe <- queryEngine
  } yield
    new PostgresMigrationEngine[IO](
      t,
      prevSyntaxTree,
      syntaxTree,
      qe,
      funcExecutor
    )

  val opParser = new OperationParser(syntaxTree)

  def migrate: IO[Unit] = migrationEngine.use(_.migrate)

  def runQuery[T](query: ConnectionIO[T]) = transactor.use(query.transact(_))

  def createProject(project: ProjectInput): IO[Unit] = {
    val record = project.toJson.asJsObject
    queryEngine
      .use { qe =>
        transactor.use { t =>
          qe.createOneRecord(ProjectModel, record, Vector.empty)
            .void
            .transact(t)
        }
      }
  }

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
                    previousMigration {
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

    queryEngine.use {
      _.run(projectQuery).map { query =>
        query.head._2.toMap
          .apply("Project")
          .head
          ._2
          .convertTo[Option[Project]]
      }
    }
  }

  def persistPreviousMigration(
      projectName: String,
      previousMigration: MigrationInput
  ): IO[Unit] = {
    val queryVars = JsObject(
      "projectName" -> projectName.toJson,
      "project" -> JsObject("previousMigration" -> previousMigration.toJson)
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
      RequestReducer(running.Request.bareReqFrom(gqlMut).copy(queryVariables = queryVars))

    val ops = opParser
      .parse(mut)
      .getOrElse {
        throw new InternalException("Invalid hard-coded query to fetch project")
      }

    queryEngine.use(_.run(ops).void)
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
