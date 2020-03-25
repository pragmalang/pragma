package parsing

import domain._, primitives._, utils._
import parsing.utils.DependencyGraph
import scala.util._

class Validator(constructs: List[PConstruct]) {

  import Validator._

  val st = SyntaxTree.fromConstructs(constructs)

  def validSyntaxTree: Try[SyntaxTree] = {
    val results = List(
      checkReservedTypeIdentifiers,
      checkTypeExistance,
      checkFieldValueType,
      checkTopLevelIdentity,
      checkModelFieldIdentity,
      checkSelfRefOptionality,
      checkUserModelCredentials,
      checkModelPrimaryFields,
      checkDirectiveArgs,
      checkCircularDeps,
      checkCreateAccessRules
    )
    val errors = results.flatMap {
      case Failure(err: UserError) => err.errors
      case Failure(err) =>
        (s"Unexpected Error: ${err.getMessage}", None) :: Nil
      case _ => Nil
    }
    if (errors.isEmpty) Success(st)
    else Failure(UserError(errors))
  }

  // Check that types don't have one of the `Validator.invalidTypeIdentifiers`
  def checkReservedTypeIdentifiers: Try[Unit] = {
    val errors = (st.models :: st.enums) collect {
      case c: Identifiable with Positioned
          if invalidTypeIdentifiers.contains(c.id) =>
        (s"Identifier `${c.id}` is reserved", c.position)
    }
    if (errors.isEmpty) Success(())
    else Failure(UserError(errors))
  }

  // Type-check the default value ot model fields.
  def checkFieldValueType: Try[Unit] = Try {
    val errors: List[ErrorMessage] = st.models.flatMap { m =>
      m.fields.foldLeft(List.empty[ErrorMessage]) { (errors, field) =>
        field.defaultValue match {
          case Some(v: PValue) if field.isOptional => {
            val typesMatch = field.ptype match {
              case POption(t) => t == v.ptype
              case _          => false
            }
            if (typesMatch) errors
            else
              errors :+ (
                s"Invalid default value of type `${displayPType(v.ptype)}` for optional field `${field.id}` of type `${displayPType(field.ptype)}`",
                field.position
              )
          }
          case Some(arr: PArrayValue)
              if !Validator.arrayIsHomogeneous(arr) ||
                arr.ptype != field.ptype =>
            errors :+ (
              s"Invalid values for array field `${field.id}` (all array elements must have the same type)",
              field.position
            )
          case Some(PStringValue(value))
              if field.ptype.isInstanceOf[PReference] => {
            val found = st.enums
              .find(_.id == field.ptype.asInstanceOf[Identifiable].id)
              .flatMap(foundEnum => foundEnum.values.find(_ == value))
              .isDefined
            if (found) errors
            else
              errors :+ (
                s"The default value given to field `${field.id}` is not a member of a defined ${field.ptype.asInstanceOf[Identifiable].id} enum",
                field.position
              )
          }
          case Some(v: PValue) if v.ptype != field.ptype =>
            errors :+ (
              s"Invalid default value of type `${displayPType(v.ptype)}` for field `${field.id}` of type `${displayPType(field.ptype)}`",
              field.position
            )
          case _ => errors
        }
      }
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
          field.ptype match {
            case r: PReference => {
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
        case f if f.ptype == PSelf(m.id) && !f.isOptional =>
          s"Recursive field `${f.id}` of `${m.id}` must be optional" ->
            f.position
      }
    }
    if (!errors.isEmpty) throw new UserError(errors)
  }

  // A user model's public credential can only be String or Integer.
  // A secret credential can only be a String
  def checkCredentialTypes(model: PModel): List[ErrorMessage] =
    if (!model.isUser) Nil
    else {
      val publicField =
        model.fields.find(_.directives.exists(_.id == "publicCredential"))
      val secretField =
        model.fields.find(_.directives.exists(_.id == "secretCredential"))
      publicField zip secretField match {
        case None => Nil
        case Some((pc, sc)) => {
          val pcError = pc.ptype match {
            case PString | PInt => Nil
            case t: PType =>
              (
                s"Invalid type `${utils.displayPType(t)}` for public credential `${pc.id}` (must be either String or Integer)",
                pc.position
              ) :: Nil
          }
          val scError = sc.ptype match {
            case PString => Nil
            case t: PType =>
              (
                s"Invalid type `${utils.displayPType(t)}` for secret credential `${pc.id}` (must String)",
                pc.position
              ) :: Nil
          }
          pcError ::: scError
        }
      }
    }

  // Each user model must have exactly one public credential and
  // exactly one secret credential.
  def checkCredentialCount(model: PModel): List[ErrorMessage] =
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

  def checkModelPrimaryFields: Try[Unit] = Try {
    val errors = st.models.collect {
      case m if Validator.primaryFieldCount(m) > 1 =>
        (s"Multiple primary fields defined for `${m.id}`", m.position) :: Nil
      case m => {
        val found = Validator.findPrimaryField(m)
        if (found.isDefined) found.get.ptype match {
          case PString | PInt => Nil
          case t =>
            (
              s"Invalid type `${displayPType(t)}` for primary field `${found.get.id}` of `${m.id}` (must be String or Integer)",
              found.get.position
            ) :: Nil
        } else Nil
      }
    }.flatten
    if (!errors.isEmpty) new UserError(errors)
  }

  def checkDirectiveAgainst(
      defs: Map[String, PInterface],
      dir: Directive
  ): List[ErrorMessage] = defs.get(dir.id) match {
    case None =>
      (s"Directive `${dir.id}` is not defined", dir.position) :: Nil
    case Some(definedDir)
        if dir.args.value.keys.toList
          .diff(definedDir.fields.map(_.id)) == Nil =>
      Nil
    case Some(definedDir) => {
      val invalidArgs =
        dir.args.value.keys.toList.diff(definedDir.fields.map(_.id))
      invalidArgs.map { arg =>
        (
          s"`$arg` is not a parameter of directive `${dir.id}`",
          dir.position
        )
      }
    }
  }

  def checkDirectiveArgs: Try[Unit] = {
    val modelLevelErrors = for {
      model <- st.models
      dir <- model.directives
    } yield checkDirectiveAgainst(BuiltInDefs.modelDirectives(model), dir)

    val fieldLevelErrors = for {
      model <- st.models
      field <- model.fields
      dir <- field.directives
    } yield
      checkDirectiveAgainst(BuiltInDefs.fieldDirectives(model, field), dir)

    val allErrors = (modelLevelErrors ::: fieldLevelErrors).flatten
    if (allErrors.isEmpty) Success(())
    else Failure(new UserError(allErrors))
  }

  def checkCircularDeps: Try[Unit] = {
    val circularDeps = DependencyGraph(st).circularDeps
    lazy val errors = circularDeps map {
      case (m1, m2) =>
        s"Invalid circular dependency between `$m1` and `$m2` (the reference field in one of the models must be made optional)" ->
          None
    }
    if (circularDeps.isEmpty) Success(())
    else Failure(UserError(errors))
  }

  def checkCreateAccessRules: Try[Unit] = {
    val rules = st.permissions match {
      case None => Nil
      case Some(permissions) =>
        permissions.globalTenant.rules :::
          permissions.globalTenant.roles.flatMap(_.rules)
    }
    val errors = for {
      rule <- rules
      if rule.actions.contains(Create)
      resourceField <- rule.resourcePath._2
    } yield
      (
        s"`CREATE` event is not allowed to be specified for access rules on field resources (`${resourceField}`, in this case)",
        rule.position
      )
    if (errors.isEmpty) Success(())
    else Failure(UserError(errors))
  }

}
object Validator {

  def arrayIsHomogeneous(arr: PArrayValue): Boolean =
    arr.values.forall(_.ptype == arr.elementType)

  def primaryFieldCount(model: PModel) =
    model.fields.count(field => field.directives.exists(_.id == "primary"))

  def findPrimaryField(model: PModel): Option[PModelField] =
    model.fields.find(_.directives.exists(_.id == "primary"))

  val invalidTypeIdentifiers = List(
    "WhereInput",
    "OrderByInput",
    "OrderEnum",
    "RangeInput",
    "FilterInput",
    "MatchesInput",
    "ComparisonInput",
    "EVENT_ENUM",
    "Any"
  )

}
