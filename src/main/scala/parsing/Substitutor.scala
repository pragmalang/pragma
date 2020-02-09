package parsing

import domain._, primitives._, utils._, HeavenlyParser.Reference
import scala.util.{Try, Success, Failure}
import scala.jdk.CollectionConverters._
import scala.collection.immutable.ListMap
import running.pipeline.PipelineInput
import running.pipeline.PipelineOutput

object Substitutor {

  def substitute(st: SyntaxTree): Try[SyntaxTree] = {
    val ctx = getContext(st.imports) match {
      case Success(ctx) => ctx
      case Failure(err) => return Failure(err)
    }
    val substitutedModels =
      st.models.map(substituteDirectiveFunctionRefs(_, ctx))
    val modelErrors = substitutedModels.collect {
      case Failure(err: UserError) => err.errors
    }.flatten
    val substitutedAcl =
      st.permissions.map(acl => substitutePredicateRefs(acl, ctx.value))
    val aclErrors = substitutedAcl match {
      case Some(Success(_))              => Nil
      case None                          => Nil
      case Some(Failure(err: UserError)) => err.errors
      case Some(Failure(err)) =>
        (err.getMessage, st.permissions.flatMap(_.position)) :: Nil
    }
    val allErrors = modelErrors ::: aclErrors
    if (allErrors.isEmpty)
      Success(
        addDefaultPrimaryFields(
          st.copy(
            models = substitutedModels.map(_.get),
            permissions = substitutedAcl.map(_.get)
          )
        )
      )
    else
      Failure(new UserError(allErrors))

  }

  // Gets all  imported functions as a single object
  def getContext(imports: List[HImport]): Try[HInterfaceValue] = {
    val importedObjects = imports zip imports.map(readGraalFunctions)
    val importErrors = importedObjects.collect {
      case (imp, Failure(exception)) => (exception.getMessage, imp.position)
    }
    if (!importErrors.isEmpty) Failure(new UserError(importErrors))
    else
      Success {
        HInterfaceValue(
          ListMap.from(importedObjects.map(imp => (imp._1.id, imp._2.get))),
          HInterface(
            "context",
            importedObjects.map(
              imp => HInterfaceField(imp._1.id, HAny, None)
            ),
            None
          )
        )
      }
  }

  def readGraalFunctions(himport: HImport): Try[HInterfaceValue] = Try {
    import org.graalvm.polyglot._
    val graalCtx = Context.create("js")
    val file = new java.io.File(himport.filePath)
    val languageId = Source.findLanguage(file)
    val source = Source.newBuilder(languageId, file).build()
    graalCtx.eval(source)
    val defKeys = graalCtx.getBindings(languageId).getMemberKeys()
    val hobj = ListMap.from(
      defKeys.asScala.map { defId =>
        defId -> GraalFunction(
          id = defId,
          htype = HFunction(ListMap.empty, HAny),
          filePath = himport.filePath,
          graalCtx,
          languageId
        )
      }
    )
    val ctxHtype = HInterface(
      himport.id,
      hobj.keys
        .map(k => HInterfaceField(k, HFunction(ListMap.empty, HAny), None))
        .toList,
      None
    )
    HInterfaceValue(hobj, ctxHtype)
  }

  def getReferencedFunction(
      ref: Reference,
      ctx: HObject
  ): Option[GraalFunction] =
    ctx.get(ref.id) match {
      case Some(f: GraalFunction) if !ref.child.isDefined => Some(f)
      case Some(HInterfaceValue(hobj, _)) if ref.child.isDefined =>
        getReferencedFunction(ref.child.get, hobj)
      case _ => None
    }

  def substituteDirectiveFunctionRefs(
      model: HModel,
      ctx: HInterfaceValue
  ): Try[HModel] = {
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
            HModelField(
              f.id,
              f.htype,
              f.defaultValue,
              dirs.map(_.get),
              f.position
            )
        }
        HModel(
          model.id,
          newFields,
          newModelLevel.map(_.get),
          model.position
        )
      }
  }

  def substituteDirectiveRefArgs(
      ctx: HInterfaceValue
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
    if (!argErrors.isEmpty) Failure(new UserError(argErrors.toList))
    else
      Success(
        dir.copy(
          args =
            HInterfaceValue(newArgs.map(p => (p._1, p._2.get)), dir.args.htype)
        )
      )
  }

  def substituteAccessRulePredicate(
      rule: AccessRule,
      ctx: HObject
  ): Try[AccessRule] = {
    val newPredicate = rule.predicate match {
      case ref: Reference =>
        (getReferencedFunction(ref, ctx), ref.position)
      case f: HFunctionValue[_, _] =>
        (Some(f), rule.position)
    }
    if (newPredicate._1.isDefined)
      Success {
        rule.copy(predicate = newPredicate._1.get)
      } else
      Failure {
        new UserError(
          (
            s"Predicate `${rule.predicate.asInstanceOf[Reference]}` is not defined",
            newPredicate._2
          ) :: Nil
        )
      }
  }

  def substituteTenant(t: Tenant, ctx: HObject): Try[Tenant] = {
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
      ctx: HObject
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

  // Adds an _id: String @primary field to the model
  def withDefaultId(model: HModel) = model.copy(
    fields = HModelField(
      "_id",
      HString,
      None,
      Directive(
        "primary",
        HInterfaceValue(ListMap.empty, HInterface("primary", Nil, None)),
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
