package setup.storage.postgres

import cats.effect.IO
import instances._
import cats.implicits._
import cats.kernel.Monoid

case class PostgresMigration(
    steps: Vector[SQLMigrationStep],
    preScripts: Vector[IO[Unit]]
) {
  def run: IO[Unit] = ???
  def ++(that: PostgresMigration): PostgresMigration = this.combine(that)
  def concat(that: PostgresMigration): PostgresMigration = this.combine(that)
  def renderSQL: String =
    "CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";\n\n" + steps
      .map(_.renderSQL)
      .mkString("\n\n")
}
object PostgresMigration {
  def empty: PostgresMigration = Monoid[PostgresMigration].empty
}
