package pragma.domain

import pragma.domain.utils._
import pragma.parsing.{PragmaParser, Validator}
import pragma.parsing.substitution._
import scala.util.Try

case class SyntaxTree(
    imports: Seq[PImport],
    models: Seq[PModel],
    enums: Seq[PEnum],
    permissions: Permissions,
    config: PConfig
) {
  lazy val modelsById: Map[ID, PModel] = models.map(_.id).zip(models).toMap

  lazy val enumsById: Map[ID, PEnum] = enums.map(_.id).zip(enums).toMap

  lazy val importsById: Map[ID, PImport] = imports.map(_.id).zip(imports).toMap

  def findTypeById(id: String): Option[PType] =
    modelsById.get(id) orElse enumsById.get(id)

  lazy val functions: Set[PFunctionValue] = models.flatMap { model =>
    model.readHooks ++ model.writeHooks ++ model.deleteHooks ++ model.loginHooks
  }.toSet ++ {
    val fnsFrom: Seq[AccessRule] => Seq[PFunctionValue] = rules =>
      rules.collect {
        case AccessRule(_, _, _, Some(fn), _, _) => fn
      }
    fnsFrom(permissions.globalTenant.rules).toSet ++
      fnsFrom(permissions.globalTenant.roles.flatMap(_.rules)).toSet
  }

  def render: String =
    (models ++ enums)
      .map(displayPType(_, true))
      .mkString("\n\n")
}
object SyntaxTree {
  /** The resulting syntax tree is validated and substituted */
  def from(code: String): Try[SyntaxTree] =
    new PragmaParser(code).syntaxTree
      .run()
      .flatMap(new Validator(_).validSyntaxTree)
      .flatMap(Substitutor.substitute)

  def empty: SyntaxTree =
    SyntaxTree(
      Seq.empty,
      Seq.empty,
      Seq.empty,
      Permissions.empty,
      PConfig(Nil, None)
    )
}
