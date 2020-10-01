package pragma.parsing.substitution

import pragma.domain._, utils._, pragma.parsing.PragmaParser.Reference
import scala.util.{Try, Success, Failure}
import cats.implicits._

object Substitutor {

  def substitute(st: SyntaxTree): Try[SyntaxTree] = {
    val imports = st.imports.toList.traverse { imp =>
      Try(new java.io.File(imp.filePath)).flatMap { file =>
        if (!file.canRead)
          Failure(
            UserError(
              (
                "Cannot read file/directory: " + imp.filePath,
                imp.position
              ) :: Nil
            )
          )
        else if (!file.exists)
          Failure(
            UserError(
              (s"File/directory `${imp.filePath}` does not exist", imp.position) :: Nil
            )
          )
        else Success(imp)
      }
    }

    val substitutedModels = imports.flatMap(_ => ModelSubstitutor(st))
    val modelErrors = substitutedModels match {
      case Failure(err: UserError) => err.errors.toList
      case _                       => Nil
    }

    val substitutedPermissions = PermissionsSubstitutor(st)

    val permissionsErrors = substitutedPermissions match {
      case Success(_)              => Nil
      case Failure(err: UserError) => err.errors
      case Failure(err)            => (err.getMessage, None) :: Nil
    }
    val allErrors = modelErrors.toList ++ permissionsErrors
    if (allErrors.isEmpty)
      Success(
        st.copy(
          models = substitutedModels.get,
          permissions = substitutedPermissions.get
        )
      )
    else
      Failure(UserError(allErrors))
  }

  def getReferencedFunction(
      imports: Map[String, String],
      ref: Reference
  ): Option[ExternalFunction] =
    ref.path match {
      case importAs :: fnName :: Nil if imports.contains(importAs) =>
        Some(ExternalFunction(fnName, imports(importAs)))
      case _ => None
    }

}
