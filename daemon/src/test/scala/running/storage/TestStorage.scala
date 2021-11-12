package running.storage

import pragma.domain.SyntaxTree
import running._, running.storage.postgres._
import cats.effect._
import doobie._
import pragma.jwtUtils._
import org.http4s.Uri

class TestStorage(st: SyntaxTree) {
  implicit val cs = IO.contextShift(ExecutionContexts.synchronous)

  val jc = new JwtCodec("123456")

  val t = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver",
    "jdbc:postgresql://localhost:5432/test",
    "test",
    "test",
    Blocker.liftExecutionContext(ExecutionContexts.synchronous)
  )

  implicit val queryEngine = new PostgresQueryEngine(t, st, jc)
  val functionExecutor = new PFunctionExecutor[IO](Uri.unsafeFromString("http://localhost:9832"))
  val migrationEngine = new PostgresMigrationEngine(
    t,
    st,
    queryEngine,
    functionExecutor
  )
  val storage = new Postgres[IO](migrationEngine, queryEngine)
}
