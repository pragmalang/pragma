package running.storage.postgres

import cats.effect._
import doobie.util.fragment.Fragment
import doobie.implicits._

import domain.SyntaxTree
import SQLMigrationStep._
import doobie.util.transactor.Transactor

import cats.implicits._

import domain.DomainImplicits._
import doobie._

import running.storage.postgres.instances._
import spray.json.JsObject

case class PostgresMigration(
    unorderedSteps: Vector[SQLMigrationStep]
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

  def run: ConnectionIO[Unit] = {
    renderSQL match {
      case Some(sql) =>
        Fragment(sql, Nil).update.run.map(_ => ())
      case None => {
        val effects: Vector[Option[ConnectionIO[Unit]]] = steps
          .map {
            case AlterManyFieldTypes(prevModel, changes) => {
              // val newTableTempName = "__temp_table__" + prevModel.id
              // val newModelTempDef = prevModel.copy(
              //   id = newTableTempName,
              //   fields = changes
              //     .map(change => change.field.copy(ptype = change.newType))
              //     .toSeq
              // )
              // val createNewTable = migration(CreateModel(newModelTempDef))

              // val dropPrevTable = migration(DeleteModel(prevModel))

              // val renameNewTable = migration(
              //   RenameModel(newTableTempName, prevModel.id)
              // )

              // val stream =
              //   HC.stream[JsObject](
              //     s"SELECT * FROM ${prevModel.id.withQuotes};",
              //     HPS.set(()),
              //     200
              //   )

              // val streamIO = stream.compile.toVector

              // /** TODO:
              //   *   1) Create the new table with a temp name:
              //   *   2) Load rows of prev table as a stream in memory:
              //   *     a) pass the data in each type-changed column to the correct
              //   *        type transformer if any
              //   *     b) Type check the value/s returned from the transformer
              //   *     c) try re-inserting this row in the new table
              //   *       i) Beware that Postgres auto-generated values don't get regenerated after
              //   *          this insert. This is merely just copying, make sure that no data is
              //   *          getting changed without getting passed to the correct transformer.
              //   */
              val result: Option[ConnectionIO[Unit]] = ???
              result
            }
            case step =>
              step.renderSQL.map(
                sql => Fragment(sql, Nil).update.run.map(_ => ())
              )
          }

        effects.sequence
          .map(_.sequence)
          .sequence
          .map(_ => ())
      }
    }
  }

  private[postgres] def renderSQL: Option[String] = {
    val prefix = "CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";\n\n"
    val query = steps
      .map(_.renderSQL)
      .sequence
      .map(_.mkString("\n\n"))
    query.map(prefix + _)
  }
}
