package running

package object utils {
  def conditionalRandom[U](cond: U => Boolean, generator: () => U): U = {
    val r = generator()
    if (cond(r)) r else conditionalRandom(cond, generator)
  }
}
