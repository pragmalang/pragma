package setup.storage.postgres

package object Implicits {
  implicit class IterableOfSQLMigrationStepOps(steps: Iterable[SQLMigrationStep]) {
    def renderSQL: String =
      "CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";\n\n" + steps
        .map(_.renderSQL)
        .mkString("\n\n")
  }
}
