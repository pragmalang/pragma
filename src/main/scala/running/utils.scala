package running

package object utils {
  def constrainedRandom[U](cond: U => Boolean, generator: () => U): U = {
    val r = generator()
    if (cond(r)) r else constrainedRandom(cond, generator)
  }

  class QueryError(message: String) extends Exception(s"QueryError: $message")
}
