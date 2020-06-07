package setup.storage.postgres

import cats.Monoid

package object instances {
  implicit val postgresMigrationMonoid = new Monoid[PostgresMigration] {
    override def combine(
        x: PostgresMigration,
        y: PostgresMigration
    ): PostgresMigration = PostgresMigration(x.steps ++ y.steps, x.preScripts ++ y.preScripts)
    override def empty: PostgresMigration = PostgresMigration(Vector.empty, Vector.empty)
  }
}
