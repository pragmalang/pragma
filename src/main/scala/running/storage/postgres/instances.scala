package running.storage.postgres

import cats.Monoid
import domain.SyntaxTree
import scala.language.implicitConversions

package object instances {
  import SQLMigrationStep._

  implicit val postgresMigrationMonoid = new Monoid[PostgresMigration] {
    override def combine(
        x: PostgresMigration,
        y: PostgresMigration
    ): PostgresMigration =
      PostgresMigration(
        x.unorderedSteps ++ y.unorderedSteps,
        x.preScripts ++ y.preScripts
      )
    override def empty: PostgresMigration =
      PostgresMigration(Vector.empty, Vector.empty)
  }

  implicit def sqlMigrationStepOrdering(st: SyntaxTree) =
    new Ordering[SQLMigrationStep] {
      override def compare(x: SQLMigrationStep, y: SQLMigrationStep): Int =
        (x, y) match {
          case (
              AlterTable(_, action: AlterTableAction.AddColumn),
              _: CreateTable
              ) if action.definition.isPrimaryKey =>
            -1
          case (statement: CreateTable, _)
              if st.modelsById.get(statement.name).isDefined =>
            1
          case (statement: CreateTable, _)
              if !st.modelsById.get(statement.name).isDefined =>
            -1
          case (AlterTable(_, action: AlterTableAction.AddColumn), _)
              if action.definition.isPrimaryKey =>
            1
          case (_, _) => 0
        }
    }

}
