package domain

package object utils {
  trait Identifiable {
    val id: String
  }

  type NamedArgs = Map[String, HType]
  type PositionalArgs = List[HType]
  type Args = Either[PositionalArgs, NamedArgs]
  type Date = java.time.ZonedDateTime
}
