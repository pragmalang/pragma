package running.storage.postgres

import cats.effect.IO

import domain.SyntaxTree
import SQLMigrationStep._

case class PostgresMigration(
    unorderedSteps: Vector[SQLMigrationStep],
)(implicit st: SyntaxTree) {
  def steps: Vector[SQLMigrationStep] = {
    val sqlMigrationStepOrdering = new Ordering[SQLMigrationStep] {
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
      unorderedSteps.sortWith((x, y) => sqlMigrationStepOrdering.gt(x, y))
  }

  def run: IO[Unit] = ???

  def renderSQL: String =
    "CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";\n\n" + steps
      .map(_.renderSQL)
      .mkString("\n\n")
}