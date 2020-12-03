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

  /**
    * The resulting syntax tree is not validated or substituted
    * Meant for use only in the PragmaParser
    */
  def fromConstructs(constructs: List[PConstruct]): SyntaxTree = {
    val imports = constructs.collect { case i: PImport         => i }
    val models = constructs.collect { case m: PModel           => m }
    val enums = constructs.collect { case e: PEnum             => e }
    val config = constructs.collectFirst { case cfg: PConfig   => cfg }
    val accessRules = constructs.collect { case ar: AccessRule => ar }
    val roles = constructs.collect { case r: Role              => r }
    lazy val permissions = Permissions(
      Tenant("root", accessRules, roles, None),
      Nil // TODO: Add support for user-defined tenants
    )
    SyntaxTree(
      imports,
      models,
      enums,
      permissions,
      if (config.isDefined) config.get else PConfig(Nil, None)
    )
  }

  def empty: SyntaxTree =
    SyntaxTree(
      Seq.empty,
      Seq.empty,
      Seq.empty,
      Permissions.empty,
      PConfig(Nil, None)
    )
}
