package domain
import utils._
import domain.primitives._

/**
  * An HType is a data representation (models, enums, and primitive types)
  */
trait HType

case class HConst[V <: HValue](id: String, value: V) extends Identifiable

case class HModel(
    id: String,
    fields: List[HModelField],
    directives: List[ModelDirective]
) extends HType
    with Identifiable {
  lazy val isUser = directives.exists(d => d.id == "user")
  lazy val isExposed = directives.exists(d => d.id == "expose")
}

case class HModelField(
    id: String,
    htype: HType,
    directives: List[FieldDirective],
    isOptional: Boolean
) extends Identifiable

sealed trait Directive extends Identifiable {
  val id: String
  val args: Args
}

case class ModelDirective(id: String, args: Args) extends Directive

case class FieldDirective(id: String, args: Args) extends Directive

case class ServiceDirective(id: String, args: Args) extends Directive

case class HEnum(id: String, values: List[String]) extends Identifiable

sealed trait HEvent
case object Read extends HEvent
case object Create extends HEvent
case object Update extends HEvent
case object Delete extends HEvent
case object All extends HEvent
