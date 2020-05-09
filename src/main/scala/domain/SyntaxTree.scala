package domain

import utils._
import parsing.{PragmaParser, Validator, Substitutor}
import scala.util.Try

case class SyntaxTree(
    imports: List[PImport],
    models: List[PModel],
    enums: List[PEnum],
    permissions: Permissions,
    config: Option[PConfig] = None
) {
  def findTypeById(id: String): Option[PType] =
    models.find(model => model.id.toLowerCase == id.toLowerCase) orElse
      enums.find(enum => enum.id == id)

  def render: String =
    (models ++ enums).map(displayPType(_, true)).mkString("\n\n")

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
