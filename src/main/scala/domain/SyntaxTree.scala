package domain

import utils._
import parsing.{PragmaParser, Validator, substitution}
import substitution._
import scala.util.Try

case class SyntaxTree(
    imports: Seq[PImport],
    models: Seq[PModel],
    enums: Seq[PEnum],
    permissions: Permissions,
    config: Option[PConfig] = None
) {
  lazy val modelsById: Map[ID, PModel] = models.map(_.id).zip(models).toMap

  lazy val enumsById: Map[ID, PEnum] = enums.map(_.id).zip(enums).toMap

  lazy val importsById: Map[ID, PImport] = imports.map(_.id).zip(imports).toMap

  def findTypeById(id: String): Option[PType] =
    modelsById.get(id) orElse enumsById.get(id)

  def render: String =
    (models ++ enums)
      .map(displayPType(_, true))
      .mkString("\n\n")

  def getConfigEntry(key: String): Option[ConfigEntry] =
    config.flatMap(_.getConfigEntry(key))

  lazy val relations = Relation.from(this)
}
object SyntaxTree {
  // The resulting syntax tree is validated and substituted
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
    val config = constructs.collect { case cfg: PConfig        => cfg }
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
      if (config.isEmpty) None else Some(config.head)
    )
  }
}
