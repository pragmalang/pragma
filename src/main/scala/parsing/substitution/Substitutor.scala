package parsing.substitution

import domain._, utils._, parsing.PragmaParser.Reference
import scala.util.{Try, Success, Failure}
import scala.jdk.CollectionConverters._
import org.graalvm.polyglot._

object Substitutor {

  def substitute(st: SyntaxTree): Try[SyntaxTree] = {
    val graalCtx = Context.create()
    val ctx = getContext(st.imports, graalCtx) match {
      case Success(ctx) => ctx
      case Failure(err) => return Failure(err)
    }

    val substitutedModels = ModelSubstitutor(st, ctx)
    val modelErrors = substitutedModels match {
      case Failure(err: UserError) => err.errors.toList
      case _                       => Nil
    }

    val substitutedPermissions = PermissionsSubstitutor(st, ctx)

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
          importedObjects.map(imp => (imp._1.id, imp._2.get)).toMap,
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
      pimport: PImport,
      graalCtx: Context
  ): Try[PInterfaceValue] = Try {
    val file = new java.io.File(pimport.filePath)
    val languageId = Source.findLanguage(file)
    val source = Source.newBuilder(languageId, file).build()
    graalCtx.eval(source)
    val throwawayCtx = Context.create(languageId)
    throwawayCtx.eval(source)
    val defKeys = throwawayCtx.getBindings(languageId).getMemberKeys()
    val hobj =
      defKeys.asScala.map { defId =>
        defId -> GraalFunction(
          id = defId,
          ptype = PFunction(Map.empty, PAny),
          filePath = pimport.filePath,
          graalCtx,
          languageId
        )
      }.toMap
    val ctxPtype = PInterface(
      pimport.id,
      hobj.keys
        .map(k => PInterfaceField(k, PFunction(Map.empty, PAny), None))
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

}
