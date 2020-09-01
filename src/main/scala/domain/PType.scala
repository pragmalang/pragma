package domain

import domain.utils._
import cats.implicits._

/**
  * A PType is a data representation (models, enums, and primitive types)
  */
sealed trait PType {
  def innerPReference: Option[PReference] = this match {
    case ref: PReference                  => Some(ref)
    case PArray(ref: PReference)          => Some(ref)
    case POption(ref: PReference)         => Some(ref)
    case POption(PArray(ref: PReference)) => Some(ref)
    case _                                => None
  }

  def innerPModel: Option[PModel] = this match {
    case model: PModel                  => Some(model)
    case PArray(model: PModel)          => Some(model)
    case POption(model: PModel)         => Some(model)
    case POption(PArray(model: PModel)) => Some(model)
    case _                              => None
  }
}

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
    fields.find(_.isPrimary).get

  lazy val readHooks = getHooksByName(this, "onRead")

  lazy val writeHooks = getHooksByName(this, "onWrite")

  lazy val deleteHooks = getHooksByName(this, "onDelete")

  lazy val loginHooks = getHooksByName(this, "onLogin")

  lazy val publicCredentialFields = fields.filter(_.isPublicCredential)

  lazy val secretCredentialField = fields.find(_.isSecretCredential)

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
          case Some(fn: ExternalFunction) => fn
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
) extends PShapeField {
  lazy val isPrimary = directives.exists(_.id == "primary")

  lazy val isPublicCredential = directives.exists(_.id == "publicCredential")

  lazy val isSecretCredential = directives.exists(_.id == "secretCredential")

  lazy val isUUID = directives.exists(_.id == "uuid")

  lazy val isAutoIncrement = directives.exists(_.id == "autoIncrement")

  lazy val isUnique = directives.exists(_.id == "unique")

  lazy val innerModelId: Option[String] = ptype match {
    case PArray(model: PModel)           => model.id.some
    case PArray(PReference(id))          => id.some
    case POption(PArray(model: PModel))  => model.id.some
    case POption(PArray(PReference(id))) => id.some
    case _                               => None
  }
}

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
case class PArray(ptype: PType) extends PType
case class PFile(sizeInBytes: Int, extensions: Seq[String])
    extends PrimitiveType
case class PFunction(args: NamedArgs, returnType: PType) extends PrimitiveType
case class POption(ptype: PType) extends PType
