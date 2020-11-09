package running.storage.postgres

import running.storage._
import cats._

class Postgres[M[_]: Monad](
    migrationEngine: PostgresMigrationEngine[M],
    queryEngine: PostgresQueryEngine[M]
) extends Storage[Postgres[M], M](queryEngine, migrationEngine)
