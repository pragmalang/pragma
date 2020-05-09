package parsing

import domain._, utils._, PragmaParser.Reference
import scala.util.{Try, Success, Failure}
import scala.jdk.CollectionConverters._
import scala.collection.immutable.ListMap
import org.graalvm.polyglot._

object Substitutor {

  def substitute(st: SyntaxTree): Try[SyntaxTree] = {
    val graalCtx = Context.create()
    val ctx = getContext(st.imports.values.toList, graalCtx) match {
      case Success(ctx) => ctx
      case Failure(err) => return Failure(err)
    }
    val substitutedModels =
      st.models.map(model => substituteDirectiveFunctionRefs(model._2, ctx))
    val modelErrors = substitutedModels.collect {
      case Failure(err: UserError) => err.errors
    }.flatten

    val substitutedPermissions = PermissionsSubstitutor(st, ctx.value)

    val permissionsErrors = substitutedPermissions match {
      case Success(_)              => Nil
      case Failure(err: UserError) => err.errors
      case Failure(err)            => (err.getMessage, None) :: Nil
    }
    val allErrors = modelErrors.toList ++ permissionsErrors
    if (allErrors.isEmpty)
      Success(
        st.copy(
          models = substitutedModels
            .map(_.get)
            .map(model => model.id -> model)
            .toMap,
          permissions = substitutedPermissions.get
        )
      )
    else
      Failure(UserError(allErrors))
  }

  /** Gets all imported functions as a single object */
  def getContext(
      imports: Seq[PImport],
      graalCtx: Context
  ): Try[PInterfaceValue] = {
    val importedObjects = imports zip imports.map(
      readGraalFunctionsIntoContext(_, graalCtx)
    )
    val importErrors = importedObjects.collect {
      case (imp, Failure(exception)) => (exception.getMessage, imp.position)
    }
    if (!importErrors.isEmpty) Failure(new UserError(importErrors))
    else
      Success {
        PInterfaceValue(
          ListMap.from(importedObjects.map(imp => (imp._1.id, imp._2.get))),
          PInterface(
            "context",
            importedObjects.map(
              imp => PInterfaceField(imp._1.id, PAny, None)
            ),
            None
          )
        )
      }
  }

  /** Reads code into the passed `graalCtx` and returns the
    * definitions in the import
    */
  def readGraalFunctionsIntoContext(
      himport: PImport,
      graalCtx: Context
  ): Try[PInterfaceValue] = Try {
    val file = new java.io.File(himport.filePath)
    val languageId = Source.findLanguage(file)
    val source = Source.newBuilder(languageId, file).build()
    graalCtx.eval(source)
    val throwawayCtx = Context.create(languageId)
    throwawayCtx.eval(source)
    val defKeys = throwawayCtx.getBindings(languageId).getMemberKeys()
    val hobj = ListMap.from(
      defKeys.asScala.map { defId =>
        defId -> GraalFunction(
          id = defId,
          ptype = PFunction(ListMap.empty, PAny),
          filePath = himport.filePath,
          graalCtx,
          languageId
        )
      }
    )
    val ctxPtype = PInterface(
      himport.id,
      hobj.keys
        .map(k => PInterfaceField(k, PFunction(ListMap.empty, PAny), None))
        .toList,
      None
    )
    PInterfaceValue(hobj, ctxPtype)
  }

  def getReferencedFunction(
      ref: Reference,
      ctx: Map[String, PValue]
  ): Option[GraalFunction] =
    ctx.get(ref.path.head) match {
      case Some(f: GraalFunction) if ref.path.length == 1 => Some(f)
      case Some(PInterfaceValue(hobj, _)) if ref.path.length > 1 =>
        getReferencedFunction(ref.copy(path = ref.path.tail), hobj)
      case _ => None
    }

  def substituteDirectiveFunctionRefs(
      model: PModel,
      ctx: PInterfaceValue
  ): Try[PModel] = {
    val newModelLevel = model.directives.map(substituteDirectiveRefArgs(ctx))
    val newFieldLevel = model.fields.map { field =>
      field.directives.map(substituteDirectiveRefArgs(ctx))
    }
    val errors = newModelLevel ++ newFieldLevel.flatten collect {
      case Failure(e: UserError) => e
    }
    if (errors.length > 0) Failure(new UserError(errors.flatMap(_.errors)))
    else
      Success {
        val newFields = model.fields.zip(newFieldLevel).map {
          case (f, dirs) =>
            PModelField(
              f.id,
              f.ptype,
              f.defaultValue,
              dirs.map(_.get),
              f.position
            )
        }
        PModel(
          model.id,
          newFields,
          newModelLevel.map(_.get),
          model.position
        )
      }
  }

  def substituteDirectiveRefArgs(
      ctx: PInterfaceValue
  )(
      dir: Directive
  ): Try[Directive] = {
    val newArgs = dir.args.value.map {
      case (argId, ref: Reference) =>
        (argId, getReferencedFunction(ref, ctx.value))
      case (argId, v) => (argId, Some(v))
    }
    val argErrors = newArgs.collect {
      case (name, None) =>
        (
          s"Argument `${dir.args.value(name).toString}` is not defined",
          dir.position
        )
    }
    if (!argErrors.isEmpty) Failure(UserError(argErrors.toList))
    else
      Success(
        dir.copy(
          args =
            PInterfaceValue(newArgs.map(p => (p._1, p._2.get)), dir.args.ptype)
        )
      )
  }

}

object PermissionsSubstitutor {

  /** Combines all other `PermissionsSubstitutor` methods */
  def apply(st: SyntaxTree, ctx: Map[String, PValue]): Try[Permissions] = {
    val newGlobalRules = combineUserErrorTries {
      st.permissions.globalTenant.rules
        .map(substituteAccessRule(_, None, st, ctx))
    }
    val substitutedRoles = st.permissions.globalTenant.roles.map { role =>
      val roleModel =
        st.models.values.find(model => model.id == role.user.id && model.isUser)
      roleModel match {
        case None =>
          Failure(
            UserError(
              s"User model `${role.user.id}` is not defined",
              role.position
            )
          )
        case Some(userModel) =>
          combineUserErrorTries {
            role.rules.map(substituteAccessRule(_, Some(userModel), st, ctx))
          } map { newRules =>
            role.copy(rules = newRules.toSeq)
          }
      }
    }
    val newRoles = combineUserErrorTries(substitutedRoles)

    (newGlobalRules, newRoles) match {
      case (Success(rules), Success(roles)) =>
        Success {
          st.permissions.copy(
            globalTenant = st.permissions.globalTenant
              .copy(rules = rules.toSeq, roles = roles.toSeq)
          )
        }
      case (Failure(e1: UserError), Failure(e2: UserError)) =>
        Failure(UserError(e1.errors ++ e2.errors))
      case (_, Failure(err)) => Failure(err)
      case (Failure(err), _) => Failure(err)
    }
  }

  def substituteAccessRule(
      rule: AccessRule,
      selfRole: Option[PModel],
      st: SyntaxTree,
      ctx: Map[String, PValue]
  ): Try[AccessRule] = {
    val parentName = rule.resourcePath._1.asInstanceOf[Reference].path.head
    val childRef = rule.resourcePath._2.asInstanceOf[Option[Reference]]
    val (parent, isSelfRule) =
      if (selfRole.isDefined && parentName == "self") (selfRole.get, true)
      else if (!selfRole.isDefined && parentName == "self")
        return Failure(
          UserError(
            s"`self` is not defined for rules outside a role",
            rule.position
          )
        )
      else
        (st.models.get(parentName) match {
          case Some(model) => model
          case None =>
            return Failure(
              UserError(
                s"Model `${parentName}` is not defined",
                rule.position
              )
            )
        }, false)
    val child =
      if (childRef.isDefined) {
        val foundChild = childRef flatMap { ref =>
          parent.fields.find(_.id == ref.path.head)
        }
        foundChild match {
          case None =>
            return Failure(
              UserError(
                s"`${childRef.get.path.head}` is not a field of `${parent.id}`",
                rule.position
              )
            )
          case someField => someField
        }
      } else None

    val newRule = rule.copy(resourcePath = (parent, child))

    substituteRulePredicate(newRule, isSelfRule, st.models.values.toList, ctx)
      .flatMap(substituteAccessRulePermissions(_))
  }

  /** Used as a part of access rule substitution */
  def substituteRulePredicate(
      rule: AccessRule,
      isSelfRule: Boolean,
      modelDefs: List[PModel],
      ctx: Map[String, PValue]
  ): Try[AccessRule] = {
    val userPredicate = rule.predicate match {
      case None => None
      case Some(ref: Reference) =>
        Substitutor.getReferencedFunction(ref, ctx) match {
          case None =>
            return Failure(UserError(s"Predicate `$ref` is not defined"))
          case somePredicate => somePredicate
        }
      case someFunction => someFunction
    }
    val withSelfAdditions = (rule.resourcePath._1, userPredicate) match {
      case (model, None) if isSelfRule =>
        Some(IfSelfAuthPredicate(modelDefs.find(_.id == model.id).get))
      case (model, Some(predicate)) if isSelfRule => {
        val selfModel = modelDefs.find(_.id == model.id).get
        Some(
          PredicateAnd(
            selfModel,
            IfSelfAuthPredicate(selfModel),
            predicate
          )
        )
      }
      case _ => userPredicate
    }

    Success(rule.copy(predicate = withSelfAdditions))
  }

  /** Used as a part of access rule substitution */
  def substituteAccessRulePermissions(rule: AccessRule): Try[AccessRule] = {
    import PPermission._
    val newPermissions = rule match {
      case AccessRule(_, _, permissions, _, _)
          if permissions.size > 1 && permissions.contains(All) =>
        Left(
          (
            s"`${All}` permission cannot be combined with other permissions",
            rule.position
          )
        )
      case AccessRule(_, (_, None), permissions, _, _)
          if permissions == Set(All) =>
        Right(allowedModelPermissions)
      case AccessRule(_, (_, Some(field)), permissions, _, _)
          if field.ptype.isInstanceOf[PArray] && permissions == Set(All) =>
        Right(allowedArrayFieldPermissions)
      case AccessRule(_, (_, Some(field)), permissions, _, _)
          if (field.ptype.isInstanceOf[PrimitiveType] ||
            field.ptype.isInstanceOf[PEnum]) && permissions == Set(All) =>
        Right(allowedPrimitiveFieldPermissions)
      case AccessRule(_, (_, Some(field)), permissions, _, _) if permissions == Set(All) =>
        Right(allowedModelPermissions)
      case AccessRule(_, (_, Some(field)), permissions, _, _)
          if field.ptype.isInstanceOf[PArray] =>
        permissions.find(!allowedArrayFieldPermissions.contains(_)) match {
          case None => Right(permissions)
          case Some(event) =>
            Left(
              (
                s"Permission `$event` cannot be specified for array field `${field.id}`",
                rule.position
              )
            )
        }
      case AccessRule(_, (_, Some(field)), permissions, _, _)
          if field.ptype.isInstanceOf[PrimitiveType] ||
            field.ptype.isInstanceOf[PEnum] =>
        permissions.find(!allowedPrimitiveFieldPermissions.contains(_)) match {
          case None => Right(permissions)
          case Some(event) =>
            Left(
              (
                s"Permission `$event` cannot be specified for primitive field `${field.id}`",
                rule.position
              )
            )
        }
      case AccessRule(_, (_, Some(field)), permissions, _, _) =>
        permissions.find(!allowedModelFieldPermissions.contains(_)) match {
          case None => Right(permissions)
          case Some(event) =>
            Left(
              (
                s"Permission `$event` cannot be specified for model field `${field.id}`",
                rule.position
              )
            )
        }
      case AccessRule(_, (model, None), permissions, _, _) =>
        permissions.find(!allowedModelPermissions.contains(_)) match {
          case None => Right(permissions)
          case Some(event) =>
            Left(
              (
                s"Permission `$event` cannot be specified for model `${model.id}`",
                rule.position
              )
            )
        }
    }

    newPermissions match {
      case Right(permissions) => Success(rule.copy(permissions = permissions.toSet))
      case Left(errMsg)       => Failure(UserError(errMsg :: Nil))
    }
  }
}
