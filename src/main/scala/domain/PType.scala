package domain

import domain.utils._

/**
  * A PType is a data representation (models, enums, and primitive types)
  */
sealed trait PType

case object PAny extends PType

case class PReference(id: String) extends PType with Identifiable

trait PShape extends Identifiable {
  override val id: String
  val fields: Seq[PShapeField]
}

case class PModel(
    id: String,
    fields: Seq[PModelField],
    directives: Seq[Directive],
    index: Int,
    position: Option[PositionRange]
) extends PType
    with PConstruct
    with PShape {
  import PModel._

  lazy val fieldsById: Map[FieldId, PModelField] =
    fields.map(field => field.id -> field).toMap

  lazy val isUser = directives.exists(_.id == "user")

  lazy val primaryField =
    fields.find(_.directives.exists(_.id == "primary")).get

  lazy val readHooks = getHooksByName(this, "onRead")

  lazy val writeHooks = getHooksByName(this, "onWrite")

  lazy val deleteHooks = getHooksByName(this, "onDelete")

  override def equals(that: Any): Boolean = that match {
    case model: PModel =>
      model.id == id &&
        model.fields == fields &&
        model.directives == directives &&
        model.index == index
    case _ => false
  }
}
object PModel {
  def getHooksByName(model: PModel, directiveName: String) =
    model.directives
      .filter(_.id == directiveName)
      .map { dir =>
        dir.args.value.get("function") match {
          case Some(fn: PFunctionValue[_, _]) => fn
          case None =>
            throw new InternalException(
              s"`$directiveName` directive of model `${model.id}` must have one function argument. Something must've went wrong during validation"
            )
          case _ =>
            throw new InternalException(
              s"Value provided to `$directiveName` of model `${model.id}` should be a function. Something must've went wrong during substitution"
            )
        }
      }

}

case class PInterface(
    id: String,
    fields: Seq[PInterfaceField],
    position: Option[PositionRange]
) extends PType
    with PShape //with HConstruct

case class PEnum(
    id: String,
    values: Seq[String],
    position: Option[PositionRange]
) extends Identifiable
    with PType
    with PConstruct

trait PShapeField extends Positioned with Identifiable {
  val ptype: PType
  def isOptional = ptype match {
    case POption(_) => true
    case _          => false
  }
}

case class PModelField(
    id: String,
    ptype: PType,
    defaultValue: Option[PValue],
    index: Int,
    directives: Seq[Directive],
    position: Option[PositionRange]
) extends PShapeField

case class PInterfaceField(
    id: String,
    ptype: PType,
    position: Option[PositionRange]
) extends PShapeField

sealed trait PrimitiveType extends PType
case object PString extends PrimitiveType
case object PInt extends PrimitiveType
case object PFloat extends PrimitiveType
case object PBool extends PrimitiveType
case object PDate extends PrimitiveType
case class PArray(ptype: PType) extends PrimitiveType
case class PFile(sizeInBytes: Int, extensions: Seq[String])
    extends PrimitiveType
case class PFunction(args: NamedArgs, returnType: PType) extends PrimitiveType
case class POption(ptype: PType) extends PrimitiveType
