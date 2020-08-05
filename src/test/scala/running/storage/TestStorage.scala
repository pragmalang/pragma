package running.storage

import domain.SyntaxTree
import running.storage.postgres._
import cats.effect._
import doobie._

class TestStorage(st: SyntaxTree) {
  implicit val cs = IO.contextShift(ExecutionContexts.synchronous)

  val t = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver",
    "jdbc:postgresql://localhost:5433/test",
    "test",
    "test",
    Blocker.liftExecutionContext(ExecutionContexts.synchronous)
  )

  val queryEngine = new PostgresQueryEngine(t, st)
  val migrationEngine = new PostgresMigrationEngine[IO](st)
  val storage = new Postgres[IO](migrationEngine, queryEngine)
}
