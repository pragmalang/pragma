package running.storage

import pragma.domain.SyntaxTree
import running.storage.postgres._
import cats.effect._
import doobie._
import running.JwtCodec

class TestStorage(st: SyntaxTree) {
  implicit val cs = IO.contextShift(ExecutionContexts.synchronous)

  val jc = new JwtCodec("123456")

  val t = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver",
    "jdbc:postgresql://localhost:5433/test",
    "test",
    "test",
    Blocker.liftExecutionContext(ExecutionContexts.synchronous)
  )

  val queryEngine = new PostgresQueryEngine(t, st, jc)
  val migrationEngine = PostgresMigrationEngine.initialMigration[IO](t, st, queryEngine)
  val storage = new Postgres[IO](migrationEngine, queryEngine)
}
