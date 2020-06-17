package setup.storage.postgres

import cats.effect.IO
import instances._
import cats.implicits._
import cats.kernel.Monoid
import instances.sqlMigrationStepOrdering
import domain.SyntaxTree

case class PostgresMigration(
    unorderedSteps: Vector[SQLMigrationStep],
    preScripts: Vector[IO[Unit]]
) {
  def steps(st: SyntaxTree): Vector[SQLMigrationStep] =
    unorderedSteps.sortWith((x, y) => sqlMigrationStepOrdering(st).gt(x, y))

  def run: IO[Unit] = ???
  def ++(that: PostgresMigration): PostgresMigration = this.combine(that)
  def concat(that: PostgresMigration): PostgresMigration = this.combine(that)
  def renderSQL(st: SyntaxTree): String =
    "CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";\n\n" + steps(st)
      .map(_.renderSQL)
      .mkString("\n\n")
}
object PostgresMigration {
  def empty: PostgresMigration = Monoid[PostgresMigration].empty
}
