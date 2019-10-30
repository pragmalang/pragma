package parsing
import domain._, primitives._, utils._
import scala.util._

class Validator(val st: List[HConstruct]) {

  def results: List[ErrorMessage] = {
    val results = List(
      checkTypeExistance,
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
              case Some(v: HValue) if field.isOptional => {
                val typesMatch = field.htype match {
                  case HOption(t) => t == v.htype
                  case _          => false
                }
                if (typesMatch) errors
                else
                  errors :+ (
                    s"Invalid default value of type `${displayHType(v.htype)}` for optional field `${field.id}` of type `${displayHType(field.htype)}`",
                    field.position
                  )
              }
              case Some(arr: HArrayValue)
                  if !Validator.arrayIsHomogeneous(arr) ||
                    arr.htype != field.htype =>
                errors :+ (
                  s"Invalid values for array field `${field.id}` (all array elements must have the same type)",
                  field.position
                )
              case Some(v: HValue) if v.htype != field.htype =>
                errors :+ (
                  s"Invalid default value of type `${displayHType(v.htype)}` for field `${field.id}` of type `${displayHType(field.htype)}`",
                  field.position
                )
              case _ => errors
            }
        )
      case _ => Nil
    }.flatten
    if (!errors.isEmpty) throw new UserError(errors)
  }

  // Check that no two constructs have the same id.
  def checkIdentity(
      identifiables: List[Identifiable with Positioned]
  ): List[ErrorMessage] =
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

  def checkTopLevelIdentity: Try[Unit] = Try {
    val errors = checkIdentity(
      for (c <- st
           if c.isInstanceOf[Identifiable with Positioned])
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

  // Check if types are defined
  def checkTypeExistance: Try[Unit] = Try {
    val nonExistantFieldTypeErrors = for {
      construct <- st
      if construct.isInstanceOf[HModel]
      field <- construct.asInstanceOf[HModel].fields
    } yield
      field.htype match {
        case r: HReference => {
          val foundType = st.find {
            case m: HModel => m.id == r.id
            case e: HEnum  => e.id == r.id
            case _         => false
          }
          if (foundType.isDefined) None
          else Some((r.id + " is not defined", field.position))
        }
        case _: PrimitiveType => None
      }
    val errors = nonExistantFieldTypeErrors.flatten
    if (errors.isEmpty) throw new UserError(errors)
  }

}
object Validator {

  def arrayIsHomogeneous(arr: HArrayValue): Boolean =
    arr.values.forall(_.htype == arr.elementType)

}
