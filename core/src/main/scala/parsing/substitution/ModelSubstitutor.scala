package pragma.parsing.substitution

import pragma.domain._, utils._
import pragma.parsing.PragmaParser.Reference
import scala.util._
import cats.implicits._

object ModelSubstitutor {

  def apply(st: SyntaxTree): Try[Seq[PModel]] = {
    val importsMap = st.imports.map(i => i.id -> i).toMap
    combineUserErrorTries(st.models.map { model =>
      substituteDirectiveFunctionRefs(
        substituteArrayFieldDefaultValue(model),
        importsMap
      )
    }).map(_.map(substituteEnumTypeReferences(_, st.enums)))
  }

  private def substituteDirectiveFunctionRefs(
      model: PModel,
      imports: Map[String, PImport]
  ): Try[PModel] = {
    val newModelLevel =
      model.directives.map(substituteDirectiveRefArgs(imports))
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
      imports: Map[String, PImport]
  )(
      dir: Directive
  ): Try[Directive] = {
    val newArgs = dir.args.value.toVector.traverse {
      case (argId, ref: Reference) =>
        Substitutor.getReferencedFunction(imports, ref).map(argId -> _)
      case (argId, v) => Right((argId, v))
    }
    val newDir = newArgs.map { newArgs =>
      dir.copy(
        args = PInterfaceValue(newArgs.toMap, dir.args.ptype)
      )
    }

    newDir match {
      case Right(dir) => Success(dir)
      case Left(err)  => Failure(UserError(err :: Nil))
    }
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
