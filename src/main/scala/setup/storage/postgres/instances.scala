package setup.storage.postgres

import cats.Monoid
import setup.storage.postgres.SQLMigrationStep._

package object instances {
  implicit val postgresMigrationMonoid = new Monoid[PostgresMigration] {
    override def combine(
        x: PostgresMigration,
        y: PostgresMigration
    ): PostgresMigration =
      PostgresMigration(x.steps ++ y.steps, x.preScripts ++ y.preScripts)
    override def empty: PostgresMigration =
      PostgresMigration(Vector.empty, Vector.empty)
  }

  implicit val sqlMigrationStepOrdering = new Ordering[SQLMigrationStep] {
    override def compare(x: SQLMigrationStep, y: SQLMigrationStep): Int =
      (x, y) match {
        case (_: CreateTable, _) => 1
        case (AlterTable(_, action: AlterTableAction.AddColumn), _: CreateTable)
            if action.definition.isPrimaryKey =>
          -1
        case (AlterTable(_, action: AlterTableAction.AddColumn), _)
            if action.definition.isPrimaryKey =>
          1
        case (_, _) => 0
      }
  }
}
