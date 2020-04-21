package parsing

import domain._, primitives._, utils._, PragmaParser.Reference
import scala.util.{Try, Success, Failure}
import scala.jdk.CollectionConverters._
import scala.collection.immutable.ListMap
import org.graalvm.polyglot._

object Substitutor {

  def substitute(st: SyntaxTree): Try[SyntaxTree] = {
    val graalCtx = Context.create()
    val ctx = getContext(st.imports, graalCtx) match {
      case Success(ctx) => ctx
      case Failure(err) => return Failure(err)
    }
    val substitutedModels =
      st.models.map(substituteDirectiveFunctionRefs(_, ctx))
    val modelErrors = substitutedModels.collect {
      case Failure(err: UserError) => err.errors
    }.flatten

    val substitutedPermissions = substitutePermissions(st)

    val permissionsErrors = substitutedPermissions match {
      case Success(_)              => Nil
      case Failure(err: UserError) => err.errors
      case Failure(err)            => (err.getMessage, None) :: Nil
    }
    val allErrors = modelErrors ::: permissionsErrors
    if (allErrors.isEmpty)
      Success(
        addDefaultPrimaryFields(
          st.copy(
            models = substitutedModels.map(_.get),
            permissions = substitutedPermissions.get
          )
        )
      )
    else
      Failure(UserError(allErrors))
  }

  /** Gets all  imported functions as a single object */
  def getContext(
      imports: List[PImport],
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
      ctx: PObject
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
    val errors = newModelLevel ::: newFieldLevel.flatten collect {
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

  def substitutePermissions(st: SyntaxTree): Try[Permissions] = ???

  def substituteAccessRulePredicate(
      rule: AccessRule,
      ctx: PObject
  ): Try[AccessRule] =
    rule.predicate match {
      case None => Success(rule)
      case Some(ref: Reference) =>
        getReferencedFunction(ref, ctx) match {
          case Some(value) => Success(rule.copy(predicate = Some(value)))
          case None        => Failure(UserError(s"Predicate `$ref` is not defined"))
        }
      case Some(f: PFunctionValue[_, _]) =>
        Success(rule)
    }

  def substituteTenant(t: Tenant, ctx: PObject): Try[Tenant] = {
    val newGlobalRules = t.rules.map(substituteAccessRulePredicate(_, ctx))
    val newRoles = t.roles.map { role =>
      (role, role.rules.map(substituteAccessRulePredicate(_, ctx)))
    }
    val errors = (newGlobalRules ::: newRoles.map(_._2).flatten).collect {
      case Failure(err: UserError) => err.errors
    }.flatten
    if (errors.isEmpty)
      Success(
        t.copy(
          rules = newGlobalRules.map(_.get),
          roles = newRoles.map(pair => pair._1.copy(rules = pair._2.map(_.get)))
        )
      )
    else Failure(new UserError(errors))
  }

  def substitutePredicateRefs(
      acl: Permissions,
      ctx: PObject
  ): Try[Permissions] = {
    val newGlobalTenant = substituteTenant(acl.globalTenant, ctx)
    val newTenants = acl.tenants.map(substituteTenant(_, ctx))
    val errors = (newGlobalTenant :: newTenants).collect {
      case Failure(err: UserError) => err.errors
    }.flatten
    if (errors.isEmpty)
      Success(
        acl.copy(
          globalTenant = newGlobalTenant.get,
          tenants = newTenants.map(_.get)
        )
      )
    else Failure(new UserError(errors))
  }

  // 1
  def substituteAccessRuleResource(
      rule: AccessRule,
      selfRole: Option[PSelf],
      st: SyntaxTree
  ): Try[AccessRule] = {
    val parentRef = rule.resourcePath._1.asInstanceOf[Reference]
    val childRef = rule.resourcePath._2.asInstanceOf[Option[Reference]]
    val parent =
      if (selfRole.isDefined && parentRef.path.head == "self") selfRole.get
      else if (!selfRole.isDefined && parentRef.path.head == "self")
        return Failure(
          UserError(
            s"`self` is not defined for rules outside a role",
            rule.position
          )
        )
      else
        st.models.find(_.id == parentRef.path.head) match {
          case Some(model) => model
          case None =>
            return Failure(
              UserError(
                s"Model `${parentRef.path.head}` is not defined",
                rule.position
              )
            )
        }
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

    Success(rule.copy(resourcePath = (parent, child)))
  }

  // 2
  def substituteSelfRulePredicate(
      rule: AccessRule,
      modelDefs: List[PModel]
  ): AccessRule = {
    val newPredicate = (rule.resourcePath._1, rule.predicate) match {
      case (PSelf(id), None) =>
        Some(IfSelfAuthPredicate(modelDefs.find(_.id == id).get))
      case (PSelf(id), Some(userPredicate)) => {
        val selfModel = modelDefs.find(_.id == id).get
        Some(
          PredicateAnd(
            selfModel,
            IfSelfAuthPredicate(selfModel),
            userPredicate
          )
        )
      }
      case _ => rule.predicate
    }

    rule.copy(predicate = newPredicate)
  }

  def substituteAccessRulePermissions(rule: AccessRule): Try[AccessRule] = {
    import PPermission._
    val newPermissions = rule match {
      case AccessRule(_, _, permissions, _, _)
          if permissions.length > 1 && permissions.contains(All) =>
        Left(
          (
            s"`${All}` permission cannot be combined with other permissions",
            rule.position
          )
        )
      case AccessRule(_, (_, None), All :: Nil, _, _) =>
        Right(allowedModelPermissions)
      case AccessRule(_, (_, Some(field)), All :: Nil, _, _)
          if field.ptype.isInstanceOf[PArray] =>
        Right(allowedArrayFieldPermissions)
      case AccessRule(_, (_, Some(field)), All :: Nil, _, _)
          if field.ptype.isInstanceOf[PrimitiveType] ||
            field.ptype.isInstanceOf[PEnum] =>
        Right(allowedPrimitiveFieldPermissions)
      case AccessRule(_, (_, Some(field)), All :: Nil, _, _) =>
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
          case Some(event) => {
            println(
              "Rule parent ref ptype: " + rule.resourcePath._1
                .getClass()
                .getCanonicalName()
            )
            Left(
              (
                s"Permission `$event` cannot be specified for primitive field `${field.id}`",
                rule.position
              )
            )
          }
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
      case Right(permissions) => Success(rule.copy(actions = permissions))
      case Left(errMsg)       => Failure(UserError(errMsg :: Nil))
    }
  }

  // 3
  def substitutePermissionsEvents(p: Permissions): Try[Permissions] = {
    val newGlobalRules =
      p.globalTenant.rules.map(substituteAccessRulePermissions)
    val newRoles = p.globalTenant.roles.map { role =>
      (role, role.rules.map(substituteAccessRulePermissions))
    }

    val errors = (newGlobalRules ::: newRoles.flatMap(_._2)).collect {
      case Failure(err: UserError) => err.errors
    }.flatten

    if (errors.isEmpty) Success {
      p.copy(
        globalTenant = p.globalTenant.copy(
          rules = newGlobalRules.map(_.get),
          roles = newRoles.map(role => role._1.copy(rules = role._2.map(_.get)))
        )
      )
    } else Failure(UserError(errors))
  }

  // Adds an _id: String @primary field to the model
  def withDefaultId(model: PModel) = model.copy(
    fields = PModelField(
      "_id",
      PString,
      None,
      Directive(
        "primary",
        PInterfaceValue(ListMap.empty, PInterface("primary", Nil, None)),
        FieldDirective,
        None
      ) :: Nil,
      None
    ) :: model.fields
  )

  def addDefaultPrimaryFields(st: SyntaxTree): SyntaxTree =
    st.copy(models = st.models.map { model =>
      val foundPrimaryField = Validator.findPrimaryField(model)
      if (foundPrimaryField.isDefined) model
      else withDefaultId(model)
    })

}
