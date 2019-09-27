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

  def substituteTypes: Try[List[HConstruct]] = Try {
    st collect {
      case HModel(id, fields, directives, position) => {
        val newFields = fields.map { field =>
          field.htype match {
            case m: HModel => {
              val foundModel = models.find(_.id == m.id)
              val foundEnum = enums.find(_.id == m.id)
              (foundModel, foundEnum) match {
                case (Some(model), None) =>
                  HModelField(
                    field.id,
                    model,
                    field.defaultValue,
                    field.directives,
                    field.isOptional,
                    field.position
                  )
                case (None, Some(en)) =>
                  HModelField(
                    field.id,
                    en,
                    field.defaultValue,
                    field.directives,
                    field.isOptional,
                    field.position
                  )
                case _ => throw new Exception(s"Type ${m.id} is not defined")
              }
            }
            case _: PrimitiveType => field
          }
        }
        HModel(id, newFields, directives, position)
      }
      case otherConstruct => otherConstruct
    }
  }

}
