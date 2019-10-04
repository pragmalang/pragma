package domain
import scala.collection.immutable.ListMap
import primitives._

package object utils {
  trait Identifiable {
    val id: String
  }

  type NamedArgs = ListMap[String, HType]
  type PositionalArgs = List[HType]
  type Args = Either[PositionalArgs, NamedArgs]
  type Date = java.time.ZonedDateTime

  class InternalException(message: String)
      extends Exception(s"Internal Exception: ${message}")
  class TypeMismatchException(expected: List[HType], found: HType)
      extends InternalException(
        s"Type Mismatch. Expected ${if (expected.length == 1) displayHType(expected.head)
        else s"one of [${expected.map(displayHType(_)).mkString(", ")}]"}, but found ${displayHType(found)}"
      )

  type ErrorMessage = (String, Option[PositionRange])

  class UserError(val errors: List[ErrorMessage])
      extends Exception(errors.map(_._1).mkString("\n"))

  def displayHType(hType: HType): String = hType match {
    case HString           => "String"
    case HInteger          => "Integer"
    case HFloat            => "Float"
    case HBool             => "Boolean"
    case HDate             => "Date"
    case HFile(size, exts) => s"File { size = $size, extensions = $exts }"
    case HArray(t)         => s"[${displayHType(t)}]"
    case HOption(t)        => s"${displayHType(t)}?"
    case HReference(id)    => id
    case i: Identifiable   => i.id
    case HFunction(namedArgs, returnType) => {
      val args = namedArgs
        .map(arg => displayHType(arg._2))
        .mkString(", ")
      s"($args) => ${displayHType(returnType)}"
    }
  }
}
