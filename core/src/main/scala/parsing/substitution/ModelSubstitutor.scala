package pragma.parsing.substitution

import pragma.domain._, utils._
import pragma.parsing.PragmaParser.Reference
import scala.util._

object ModelSubstitutor {

  def apply(st: SyntaxTree): Try[Seq[PModel]] = {
    val importsMap = st.imports.map(i => i.id -> i.filePath).toMap
    combineUserErrorTries(st.models.map { model =>
      substituteDirectiveFunctionRefs(
        substituteArrayFieldDefaultValue(model),
        importsMap
      )
    }).map(_.map(substituteEnumTypeReferences(_, st.enums)))
  }

  private def substituteDirectiveFunctionRefs(
      model: PModel,
      imports: Map[String, String]
  ): Try[PModel] = {
    val newModelLevel = model.directives.map(substituteDirectiveRefArgs(imports))
    val newFieldLevel = model.fields.map { field =>
      field.directives.map(substituteDirectiveRefArgs(imports))
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
              f.index,
              dirs.map(_.get),
              f.position
            )
        }
        PModel(
          model.id,
          newFields,
          newModelLevel.map(_.get),
          model.index,
          model.position
        )
      }
  }

  private def substituteDirectiveRefArgs(
      imports: Map[String, String]
  )(
      dir: Directive
  ): Try[Directive] = {
    val newArgs = dir.args.value.map {
      case (argId, ref: Reference) =>
        (argId, Substitutor.getReferencedFunction(imports, ref))
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

  private def substituteArrayFieldDefaultValue(
      model: PModel
  ): PModel = {
    val newFields = model.fields map { field =>
      field.ptype match {
        case PArray(elemType) if !field.defaultValue.isDefined =>
          field.copy(defaultValue = Some(PArrayValue(Nil, elemType)))
        case _ => field
      }
    }
    new PModel(
      model.id,
      newFields,
      model.directives,
      model.index,
      model.position
    )
  }

  private def substituteEnumTypeReferences(
      model: PModel,
      enumDefs: Seq[PEnum]
  ): PModel = {
    val newFields = model.fields.map { field =>
      field.ptype match {
        case PReference(id) => {
          val foundEnum = enumDefs.find(_.id == id)
          foundEnum match {
            case Some(e) => field.copy(ptype = e)
            case None    => field
          }
        }
        case PArray(PReference(id)) => {
          val foundEnum = enumDefs.find(_.id == id)
          foundEnum match {
            case Some(e) => field.copy(ptype = PArray(e))
            case None    => field
          }
        }
        case POption(PReference(id)) => {
          val foundEnum = enumDefs.find(_.id == id)
          foundEnum match {
            case Some(e) => field.copy(ptype = POption(e))
            case None    => field
          }
        }
        case _ => field
      }
    }
    model.copy(fields = newFields)
  }

}
