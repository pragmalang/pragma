package parsing.substitution

import domain._, utils._
import parsing.PragmaParser.Reference
import scala.util.{Try, Success, Failure}

object ModelSubstitutor {

  def apply(st: SyntaxTree, ctx: PInterfaceValue): Try[Seq[PModel]] = {
    combineUserErrorTries(st.models.map { model =>
      substituteDirectiveFunctionRefs(
        substituteOptionalArrayFieldDefaultValue(model),
        ctx
      )
    })
  }

  private def substituteDirectiveFunctionRefs(
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

  private def substituteDirectiveRefArgs(
      ctx: PInterfaceValue
  )(
      dir: Directive
  ): Try[Directive] = {
    val newArgs = dir.args.value.map {
      case (argId, ref: Reference) =>
        (argId, Substitutor.getReferencedFunction(ref, ctx.value))
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

  private def substituteOptionalArrayFieldDefaultValue(
      model: PModel
  ): PModel = {
    val newFields = model.fields map {
      case field @ PModelField(_, POption(PArray(innerType)), default, _, _) =>
        if (default.isDefined) field
        else field.copy(defaultValue = Some(PArrayValue(Nil, innerType)))
      case nonOptionalArrayField => nonOptionalArrayField
    }
    new PModel(model.id, newFields, model.directives, model.position)
  }

}
