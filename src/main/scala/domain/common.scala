package domain
import utils._
import domain.primitives._

/**
  * An HType is a data representation (models, enums, and primitive types)
  */
trait HType

case class HConst[V, HT <: HType, L <: Literal[V, HT]](id: String, value: L)
    extends Identifiable

case class HModel(id: String, fields: List[HField[HType]], directives: List[Directive])
    extends HType
    with Identifiable

case class HField[T <: HType](id: String, dataType: T, directives: List[Directive])
    extends Identifiable

sealed trait Directive extends Identifiable {
  val id: String
  val args: DirectiveArgs
}

case class ModelDirective(id: String, args: DirectiveArgs) extends Directive

case class FieldDirective(id: String, args: DirectiveArgs) extends Directive

case class ServiceDirective(id: String, args: DirectiveArgs) extends Directive

case class HEnum(id: String, values: List[String]) extends Identifiable

sealed trait HEvent
case object Read extends HEvent
case object Create extends HEvent
case object Update extends HEvent
case object Delete extends HEvent
case object All extends HEvent
