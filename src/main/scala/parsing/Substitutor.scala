package parsing
import scala.util._
import domain._, primitives._
import domain.utils.`package`.Identifiable

class Substitutor(st: List[HConstruct]) {

  def run: Try[List[HConstruct]] = Try {
    val withSubstitutedTypes = substituteTypes.get
    withSubstitutedTypes
  }

  val models = st.filter(_.isInstanceOf[HModel]).map(_.asInstanceOf[HModel])
  val enums = st.filter(_.isInstanceOf[HEnum]).map(_.asInstanceOf[HEnum])

  def substitutePlaceholderModel(m: HModel): HType = {
    val foundModel = models.find(_.id == m.id).map(substituteActualModel)
    val foundEnum = enums.find(_.id == m.id)
    (foundModel, foundEnum) match {
      case (Some(model), None) => substituteActualModel(model)
      case (None, Some(en))    => en
      case _                   => throw new Exception(s"Type ${m.id} is not defined")
    }
  }

  def substituteFieldType(field: HModelField): HModelField = {
    val newType = field.htype match {
      case m: HModel => substitutePlaceholderModel(m)
      case arrayType: HArray =>
        arrayType.htype match {
          case p: PrimitiveType => arrayType
          case m: HModel        => HArray(substitutePlaceholderModel(m))
        }
      case optionalType: HOption =>
        optionalType.htype match {
          case p: PrimitiveType => optionalType
          case m: HModel        => HOption(substitutePlaceholderModel(m))
        }
      case p: PrimitiveType => p
    }
    HModelField(
      field.id,
      newType,
      field.defaultValue,
      field.directives,
      field.isOptional,
      field.position
    )
  }

  def substituteActualModel(model: HModel): HModel = {
    val HModel(id, fields, directives, position) = model
    val newFields = fields.map(substituteFieldType)
    HModel(id, newFields, directives, position)
  }

  def substituteTypes: Try[List[HConstruct]] = Try {
    st collect {
      case m: HModel      => substituteActualModel(m)
      case otherConstruct => otherConstruct
    }
  }

}
