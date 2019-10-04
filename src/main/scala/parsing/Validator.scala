package parsing
import domain._, primitives._, utils._
import scala.util._

class Validator(val st: List[HConstruct]) {

  def results: List[ErrorMessage] = {
    val results = List(
      checkFieldValueType,
      checkTopLevelIdentity,
      checkModelFieldIdentity
    )
    results.foldLeft(List.empty[ErrorMessage])(
      (errors, result) =>
        result match {
          case Failure(err: UserError) =>
            errors ::: err.errors
          case Failure(err) =>
            (s"Unexpected Error: ${err.getMessage}", None) :: errors
          case _ => errors
        }
    )
  }

  // Type-check the default value ot model fields.
  def checkFieldValueType: Try[Unit] = Try {
    val errors: List[ErrorMessage] = st.collect {
      case m: HModel =>
        m.fields.foldLeft(List.empty[ErrorMessage])(
          (errors, field) =>
            field.defaultValue match {
              case Some(HOptionValue(_, valueType))
                  if valueType != field.htype =>
                errors :+ (
                  s"Invalid default value of type `${displayHType(valueType)}` for optional field `${field.id}` of type `${displayHType(field.htype)}`",
                  field.position
                )
              case Some(arr: HArrayValue)
                  if !Validator.arrayIsHomogeneous(arr) =>
                errors :+ (
                  s"Invalid values for array field `${field.id}` of type `${displayHType(field.htype)}` (array elements must have the same type)",
                  field.position
                )
              case Some(HArrayValue(values, elementType))
                  if elementType != field.htype =>
                errors :+ (
                  s"Invalid default values of type `${displayHType(elementType)}` for array field `${field.id}` of type `${displayHType(field.htype)}`",
                  field.position
                )
              case Some(v: HValue) if v.htype != field.htype =>
                errors :+ (
                  s"Invalid default values of type `${displayHType(v.htype)}` for field `${field.id}` of type `${displayHType(field.htype)}`",
                  field.position
                )
              case _ => Nil
            }
        )
      case _ => Nil
    }.flatten
    if (!errors.isEmpty) throw new UserError(errors)
  }

  // Check that no two constructs have the same id.
  def checkIdentity(
      identifiables: List[Identifiable with Positioned]
  ): List[ErrorMessage] = {
    identifiables
      .foldLeft((Set.empty[String], List.empty[ErrorMessage]))(
        (acc, construct) =>
          if (acc._1(construct.id))
            (
              acc._1,
              acc._2 :+ (
                s"`${construct.id}` is defined twice",
                construct.position
              )
            )
          else (acc._1 + construct.id, acc._2)
      )
      ._2
  }

  def checkTopLevelIdentity: Try[Unit] = Try {
    val errors = checkIdentity(
      for (c <- st
           if c.isInstanceOf[Identifiable] && c.isInstanceOf[Positioned])
        yield c.asInstanceOf[Identifiable with Positioned]
    )
    if (!errors.isEmpty) throw new UserError(errors)
  }

  // Check that no two fields of a model have the same id.
  def checkModelFieldIdentity: Try[Unit] = Try {
    val errors =
      for (construct <- st; if construct.isInstanceOf[HModel]) yield {
        val model = construct.asInstanceOf[HModel]
        checkIdentity(model.fields)
      }
    if (!errors.isEmpty) throw new UserError(errors.flatten)
  }

}
object Validator {

  def arrayIsHomogeneous(arr: HArrayValue): Boolean =
    arr.values.map(_.htype == arr.elementType).fold(true)(_ && _)

}
