package daemon

import cats.implicits._
import cats.effect._
import cats.MonadError

package object utils {
  def transaction[M[_]: Sync, A](steps: Step[M, A]*): Step[M, A] =
    transaction(steps.toList)

  def transaction[M[_]: Sync, A](steps: List[Step[M, A]]): Step[M, A] = {
    val (run, rollback) =
      steps.tail.toVector
        .foldLeft[(M[A], M[A])]((steps.head.run, steps.head.rollback)) {
          (acc, step) =>
            {
              val run = for {
                _ <- acc._1
                currentRun <- step.run.handleErrorWith { e =>
                  acc._2 *> MonadError[M, Throwable].raiseError(e)
                }
              } yield currentRun

              val rollback = for {
                _ <- acc._2
                currentRollback <- step.rollback
              } yield currentRollback

              (run, rollback)
            }
        }
    Step(run, rollback)
  }

  def jdbcPostgresUri(
      host: String,
      port: String,
      dbName: Option[String] = None
  ): String =
    s"jdbc:postgresql://$host:$port/${dbName.getOrElse("")}"

  def jdbcPostgresUri(uri: String): String =
    if (uri.startsWith("postgresql://"))
      s"jdbc:$uri"
    else
      s"jdbc:postgresql://$uri"

}
case class Step[M[_], A](run: M[A], rollback: M[A])
