package domain

package object utils {
  trait Identifiable {
    val id: String
  }

  type NamedArgs = Map[String, HType]
  type PositionalArgs = List[HType]
  type Args = Either[PositionalArgs, NamedArgs]
  type Date = java.time.ZonedDateTime

  class InternalException(message: String)
      extends Exception(s"Internal Exception: ${message}")
  class TypeMismatchException(expected: List[HType], found: HType)
      extends InternalException(
        s"Type Mismatch. Expected ${if (expected.length == 1) expected.head
        else s"one of [${expected.mkString(", ")}]"}, but found $found"
      )
}
