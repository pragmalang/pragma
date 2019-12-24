package parsing
import domain._, primitives._, utils._
import scala.util._
import _root_.parsing.HeavenlyParser.Reference

class Validator(constructs: List[HConstruct]) {

  val st = SyntaxTree.fromConstructs(constructs)

  def results: List[ErrorMessage] = {
    val results = List(
      checkTypeExistance,
      checkFieldValueType,
      checkTopLevelIdentity,
      checkModelFieldIdentity,
      checkSelfRefOptionality,
      checkUserModelCredentials
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
    val errors: List[ErrorMessage] = st.models.flatMap { m =>
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
    }
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
    val errors = checkIdentity(st.models ::: st.enums)
    if (!errors.isEmpty) throw new UserError(errors)
  }

  // Check that no two fields of a model have the same id.
  def checkModelFieldIdentity: Try[Unit] = Try {
    val errors = st.models.map(m => checkIdentity(m.fields))
    if (!errors.isEmpty) throw new UserError(errors.flatten)
  }

  // Check if types are defined
  def checkTypeExistance: Try[Unit] = Try {
    val types: List[Identifiable] = st.models ::: st.enums
    val nonExistantFieldTypeErrors =
      for (model <- st.models; field <- model.fields)
        yield
          field.htype match {
            case r: HReference => {
              val foundType = types.find(r.id == _.id)
              if (foundType.isDefined) None
              else Some((s"Type ${r.id} is not defined", field.position))
            }
            case _: PrimitiveType => None
          }
    val errors = nonExistantFieldTypeErrors.flatten
    if (errors.isEmpty) throw new UserError(errors)
  }

  // Check that a recursive type's self references are optional
  // (they must be in order to end the recursion)
  def checkSelfRefOptionality: Try[Unit] = Try {
    val errors = st.models.flatMap { m =>
      m.fields.collect {
        case f if f.htype == HSelf(m.id) && !f.isOptional =>
          s"Recursive field `${f.id}` of `${m.id}` must be optional" ->
            f.position
      }
    }
    if (!errors.isEmpty) throw new UserError(errors)
  }

  // A user model's public credential can only be String or Integer.
  // A secret credential can only be a String
  def checkCredentialTypes(model: HModel): List[ErrorMessage] =
    if (!model.isUser) Nil
    else {
      val publicField =
        model.fields.find(_.directives.exists(_.id == "publicCredential"))
      val secretField =
        model.fields.find(_.directives.exists(_.id == "secretCredential"))
      publicField zip secretField match {
        case None => Nil
        case Some((pc, sc)) => {
          val pcError = pc.htype match {
            case HString | HInteger => Nil
            case t: HType =>
              (
                s"Invalid type `${utils.displayHType(t)}` for public credential `${pc.id}` (must be either String or Integer)",
                pc.position
              ) :: Nil
          }
          val scError = sc.htype match {
            case HString => Nil
            case t: HType =>
              (
                s"Invalid type `${utils.displayHType(t)}` for secret credential `${pc.id}` (must String)",
                pc.position
              ) :: Nil
          }
          pcError ::: scError
        }
      }
    }

  // Each user model must have exactly one public credential and
  // exactly one secret credential.
  def checkCredentialCount(model: HModel): List[ErrorMessage] =
    if (!model.isUser) Nil
    else {
      val publicCredentialFields =
        model.fields.filter(_.directives.exists(_.id == "publicCredential"))
      val secretCredentialFields =
        model.fields.filter(_.directives.exists(_.id == "secretCredential"))
      val pcErrors =
        if (publicCredentialFields.isEmpty)
          (
            s"Missing public credential for user model `${model.id}`",
            model.position
          ) :: Nil
        else Nil
      val scErrors = if (secretCredentialFields.length > 1)
        (
          s"Multiple secret credential fields defined for user model `${model.id}` (only one is allowed)",
          model.position
        ) :: Nil
      else Nil
      pcErrors ::: scErrors
    }

  def checkUserModelCredentials: Try[Unit] = Try {
    val credentialCountErrors = st.models.flatMap(checkCredentialCount)
    val credentialTypeErrors = st.models.flatMap(checkCredentialTypes)
    val allErrors = credentialCountErrors ::: credentialTypeErrors
    if (!allErrors.isEmpty) throw new UserError(allErrors)
  }

}
object Validator {

  def arrayIsHomogeneous(arr: HArrayValue): Boolean =
    arr.values.forall(_.htype == arr.elementType)

}
