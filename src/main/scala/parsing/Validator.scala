package parsing
import domain._
import utils._
import scala.util._

class Validator(val st: List[HConstruct]) {

  def results: List[(String, Option[PositionRange])] = {
    val results =
      List(checkFieldValueType, checkIdentity, checkModelFieldUniqueness)
    results.foldLeft(List.empty[(String, Option[PositionRange])])(
      (errors, result) =>
        result match {
          case Failure(err: UserError) =>
            (err.getMessage, err.position) :: errors
          case Failure(err) =>
            (s"Unexpected Error: ${err.getMessage}", None) :: errors
          case _ => errors
        }
    )
  }

  // Type-check the default value ot model fields.
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

  // Check that no two top-level constructs have the same id.
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

  // Check that no two fields of a model have the same id.
  def checkModelFieldUniqueness: Try[Unit] = Try {
    for (construct <- st; if construct.isInstanceOf[HModel]) {
      val model = construct.asInstanceOf[HModel]
      model.fields.foldLeft(Set.empty[String])(
        (ids, field) =>
          if (ids(field.id))
            throw new UserError(
              s"Field `${field.id}` is defined twice",
              field.position
            )
          else ids + field.id
      )
    }
  }
}
