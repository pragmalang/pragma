package pragma.parsing

import pragma.domain._, utils._, DomainImplicits._
import pragma.parsing.utils.DependencyGraph
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
      checkRolesBelongToUserModels,
      checkCreateAccessRules,
      checkModelIndexUniqueness,
      checkFieldIndexUniqueness,
      checkConfigRequiredProperties
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
    val errors = (st.modelsById ++ st.enumsById) collect {
      case (id, c: Positioned) if invalidTypeIdentifiers.contains(c.id) =>
        (s"Identifier `$id` is reserved", c.position)
    }
    if (errors.isEmpty) Success(())
    else Failure(UserError(errors))
  }

  // Type-check the default value ot model fields.
  def checkFieldValueType: Try[Unit] = Try {
    val errors: List[ErrorMessage] = st.models.toList.flatMap { m =>
      m.fields.foldLeft(List.empty[ErrorMessage]) { (errors, field) =>
        field.defaultValue match {
          case Some(v: PValue) if field.isOptional => {
            val typesMatch = field.ptype match {
              case POption(t) => t == v.ptype
              case _          => false
            }
            if (typesMatch) errors
            else
              errors :+
                s"Invalid default value of type `${displayPType(v.ptype)}` for optional field `${field.id}` of type `${displayPType(field.ptype)}`" ->
                  field.position
          }
          case Some(arr: PArrayValue)
              if !Validator.arrayIsHomogeneous(arr) ||
                arr.ptype != field.ptype =>
            errors :+
              s"Invalid values for array field `${field.id}` (all array elements must have the same type)" ->
                field.position
          case Some(PStringValue(value))
              if field.ptype.isInstanceOf[PReference] => {
            val found = st.enumsById
              .get(field.ptype.asInstanceOf[Identifiable].id)
              .flatMap(foundEnum => foundEnum.values.find(_ == value))
              .isDefined
            if (found) errors
            else
              errors :+
                s"The default value given to field `${field.id}` is not a member of a defined ${field.ptype.asInstanceOf[Identifiable].id} enum" ->
                  field.position
          }
          case Some(v: PValue) if v.ptype != field.ptype =>
            errors :+
              s"Invalid default value of type `${displayPType(v.ptype)}` for field `${field.id}` of type `${displayPType(field.ptype)}`" ->
                field.position
          case _ => errors
        }
      }
    }
    if (!errors.isEmpty) throw new UserError(errors)
  }

  /** Check that no two constructs have the same id. */
  def checkIdentity(
      xs: Seq[Identifiable with Positioned]
  ): List[ErrorMessage] =
    xs.foldLeft((Set.empty[String], List.empty[ErrorMessage]))(
        (acc, construct) =>
          if (acc._1(construct.id))
            (
              acc._1,
              acc._2 :+
                s"`${construct.id}` is defined twice" -> construct.position
            )
          else if (acc._1(construct.id.small) | acc._1(construct.id.capitalize))
            (
              acc._1,
              acc._2 :+
                s"The identifier `${construct.id}` appears in two positions with the difference in the case of the first character. This causes usability issues in the generated API" -> construct.position
            )
          else (acc._1 + construct.id, acc._2)
      )
      ._2

  def checkTopLevelIdentity: Try[Unit] = {
    val errors = checkIdentity(st.models.toSeq ++ st.enums)
    if (!errors.isEmpty) Failure(UserError(errors))
    else Success(())
  }

  /** Check that no two fields of a model have the same id. */
  def checkModelFieldIdentity: Try[Unit] = Try {
    val errors = st.models.map(m => checkIdentity(m.fields))
    if (!errors.isEmpty) throw new UserError(errors.flatten)
  }

  // Check if field types are defined
  def checkTypeExistance: Try[Unit] = {
    val errors = for {
      model <- st.models
      field <- model.fields
      referenceedId <- field.ptype match {
        case PReference(id)          => Some(id)
        case PArray(PReference(id))  => Some(id)
        case POption(PReference(id)) => Some(id)
        case _                       => None
      }
      if !st.findTypeById(referenceedId).isDefined
    } yield (s"Type `$referenceedId` is not defined", field.position)
    if (errors.isEmpty) Success(())
    else Failure(UserError(errors))
  }

  // Check that a recursive type's self references are optional
  // (they must be in order to end the recursion)
  def checkSelfRefOptionality: Try[Unit] = {
    val errors = st.models.flatMap { m =>
      m.fields.collect {
        case f
            if f.ptype.isInstanceOf[PReference] &&
              f.ptype.asInstanceOf[PReference].id == m.id &&
              !f.isOptional =>
          s"Recursive field `${f.id}` of `${m.id}` must be optional" ->
            f.position
      }
    }
    if (!errors.isEmpty) Failure(UserError(errors))
    else Success(())
  }

  // A user model's public credential can only be String or Integer.
  // A secret credential can only be a String
  def checkCredentialTypes(model: PModel): List[ErrorMessage] = {

    val publicFields =
      model.publicCredentialFields
    val secretField = model.secretCredentialField
    val pcErrors = publicFields.collect {
      case PModelField(_, PString | PInt, _, _, _, _) => Nil
      case field =>
        (
          s"Invalid type `${utils.displayPType(field.ptype)}` for public credential `${field.id}` (must be either String or Integer)",
          field.position
        ) :: Nil
    }.flatten
    val scError = secretField match {
      case Some(field) if field.ptype != PString =>
        (
          s"Invalid type `${utils.displayPType(field.ptype)}` for secret credential `${field.id}` (must String)",
          field.position
        ) :: Nil
      case _ => Nil
    }
    val allErrors = pcErrors ++ scError
    if (allErrors.isEmpty) Nil
    else allErrors.toList
  }

  // Each user model must have exactly one public credential and
  // exactly one secret credential.
  def checkCredentialCount(model: PModel): List[ErrorMessage] =
    if (!model.isUser) Nil
    else {
      val publicCredentialFields =
        model.publicCredentialFields
      val secretCredentialFields =
        model.fields.filter(_.isSecretCredential)
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
    val allErrors = credentialCountErrors ++ credentialTypeErrors
    if (!allErrors.isEmpty) throw new UserError(allErrors)
  }

  def checkModelPrimaryFields: Try[Unit] = {
    val errors = st.models.collect {
      case m if Validator.primaryFieldCount(m) == 0 =>
        (s"Model `${m.id}` must have a primary field", m.position) :: Nil
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
    if (!errors.isEmpty) Failure(UserError(errors))
    else Success(())
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
    } yield checkDirectiveAgainst(BuiltInDefs.fieldDirectives(field), dir)

    val allErrors = (modelLevelErrors ++ fieldLevelErrors).flatten
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
    val rules =
      st.permissions.globalTenant.rules ++:
        st.permissions.globalTenant.roles.flatMap(_.rules)
    val errors = for {
      rule <- rules
      if rule.permissions.contains(Create)
      resourceField <- rule.resourcePath._2
    } yield
      (
        s"`CREATE` event is not allowed to be specified for access rules on field resources (`${resourceField}`, in this case)",
        rule.position
      )
    if (errors.isEmpty) Success(())
    else Failure(UserError(errors))
  }

  def checkRolesBelongToUserModels: Try[Unit] = {
    val errors = st.permissions.globalTenant.roles.filter { role =>
      !st.models.filter(_.isUser).exists(_.id == role.user.id)
    } map { userlessRole =>
      (
        s"Roles can only be defined for user models, but `${userlessRole.user.id}` is not a defined user model",
        userlessRole.position
      )
    }
    if (errors.isEmpty) Success(())
    else Failure(UserError(errors))
  }

  def checkModelIndexUniqueness: Try[Unit] = {
    val repeatedIndexess = st.models.diff(st.models.distinctBy(_.index))
    if (repeatedIndexess.length == 0) Success(())
    else {
      val errors = repeatedIndexess.map { model =>
        (
          s"Model `${model.id}` has a duplicate index ${model.index}",
          model.position
        )
      }
      Failure(UserError(errors))
    }
  }

  def checkFieldIndexUniqueness: Try[Unit] = {
    val errors = st.models.flatMap { model =>
      model.fields
        .diff(model.fields.distinctBy(_.index))
        .map { field =>
          (
            s"`${field.id}` has a duplicate index ${field.index}",
            field.position
          )
        }
    }
    if (errors.isEmpty) Success(())
    else Failure(UserError(errors))
  }

  def checkConfigRequiredProperties: Try[Unit] = {
    val config = constructs.collectFirst { case c: PConfig => c }
    config match {
      case None =>
        Failure(UserError(("`config` block is not defined", None) :: Nil))
      case Some(conf) => {
        val errors = requiredConfigEntries.toList
          .collect {
            case (key, ptype) if !conf.entryMap.contains(key) =>
              (
                s"`config` block must contain an entry named `$key` of type `${displayPType(ptype)}",
                conf.position
              )
            case (key, ptype) if conf.entryMap(key).value.ptype != ptype =>
              (
                s"Invalid value for `config` entry `$key` of type `${displayPType(ptype)}`",
                conf.entryMap(key).position
              )
          }
        if (errors.isEmpty) Success(())
        else Failure(UserError(errors))
      }
    }
  }

}
object Validator {

  def arrayIsHomogeneous(arr: PArrayValue): Boolean =
    arr.values.forall(_.ptype == arr.elementType)

  def primaryFieldCount(model: PModel) =
    model.fields.count(field => field.isPrimary)

  def findPrimaryField(model: PModel): Option[PModelField] =
    model.fields.find(_.isPrimary)

  val invalidTypeIdentifiers = List(
    "OrderByInput",
    "OrderEnum",
    "EventEnum",
    "Any",
    "Query",
    "Mutation",
    "Subscription"
  )

  val requiredConfigEntries: Map[String, PType] = Map(
    "projectName" -> PString
  )

}
