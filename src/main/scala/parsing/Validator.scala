package parsing
import domain._
import utils._
import scala.util._

class Validator(val st: List[HConstruct]) {
  def checkFieldValueType: Try[Unit] = Try {
    for {
      construct <- st
      if construct.isInstanceOf[HModel]
    } for {
      field <- construct.asInstanceOf[HModel].fields
      default <- field.defaultValue
    } if (field.htype != default.htype)
      throw new UserError(
        s"Invalid default value of type ${default.htype} for field of type ${field.htype}",
        field.position
      )
  }

  def checkIdentity: Try[Unit] = Try {
    st.foldLeft(Set.empty[String])(
      (ids, construct) =>
        construct match {
          case i: Identifiable with Positioned =>
            if (ids(i.id))
              throw new UserError(
                s"`${i.id}` is defined twice",
                i.position
              )
            else ids + i.id
          case _ => ids
        }
    )
  }
}
