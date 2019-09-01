package domain

package object utils {
  trait Identifiable {
    val id: String
  }

  type DirectiveArgs = Either[HType, Map[String, HType]]
  type Date = java.time.ZonedDateTime
}
