package parsing

import domain._, primitives._, utils.UserError
import scala.util.{Try, Success, Failure}
import scala.jdk.CollectionConverters._
import scala.collection.immutable.ListMap

object Substitutor {

  def substituteFunctionRefs(st: SyntaxTree): Try[SyntaxTree] = {
    val importedObjects = st.imports zip st.imports.map(readGraalFunctions)
    val importErrors = importedObjects.collect {
      case (imp, Failure(exception)) => (exception.getMessage, imp.position)
    }
    if (!importErrors.isEmpty) Failure(new UserError(importErrors))
    else
      Success {
        val validImports = importedObjects.map(_._2.get)
        ??? // TODO: All references need to be validated before proceeding
      }
  }

  def readGraalFunctions(himport: HImport): Try[HObject] = Try {
    import org.graalvm.polyglot._
    val graalCtx = Context.create("js")
    val file = new java.io.File(himport.filePath)
    val languageId = Source.findLanguage(file)
    val source = Source.newBuilder(languageId, file).build()
    graalCtx.eval(source)
    val defKeys = graalCtx.getBindings(languageId).getMemberKeys()
    ListMap.from(
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
  }

}
