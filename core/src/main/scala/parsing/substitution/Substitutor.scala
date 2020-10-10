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
      imports: Map[String, PImport],
      ref: Reference
  ): Either[ErrorMessage, ExternalFunction] =
    ref.path match {
      case importAs :: fnName :: Nil =>
        for {
          imp <- imports.get(importAs) match {
            case Some(i) => i.asRight
            case None =>
              (
                s"Import with identifier `$importAs` is not defined",
                ref.position
              ).asLeft
          }
          config <- imp.config match {
            case Some(c) => c.asRight
            case None =>
              (
                s"Import `$importAs` must have a configuration block specifying a runtime for the functions defined in `${imp.filePath}`",
                ref.position
              ).asLeft
          }
          runtimeEntry <- config.entryMap.get("runtime") match {
            case Some(entry) => entry.asRight
            case None =>
              (
                s"Config block of import `$importAs` must contain a `runtime` entry",
                config.position
              ).asLeft
          }
          runtimeStr <- runtimeEntry.value match {
            case PStringValue(s) => s.asRight
            case _ =>
              (
                s"`runtime` entry in config block of import `$importAs` must be a `String`",
                runtimeEntry.position
              ).asLeft
          }
        } yield
          ExternalFunction(
            fnName,
            imports(importAs).filePath,
            runtimeStr
          )
      case _ =>
        (
          s"`${ref.toString}` is referencing a function, but it is not of the form `importID.functionID`",
          ref.position
        ).asLeft
    }

}
