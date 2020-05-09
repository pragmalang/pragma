package domain

import utils._
import parsing.{PragmaParser, Validator, Substitutor}
import scala.util.Try

case class SyntaxTree(
    imports: Map[ID, PImport],
    models: Map[ID, PModel],
    enums: Map[ID, PEnum],
    permissions: Permissions,
    config: Option[PConfig] = None
) {

  def findTypeById(id: String): Option[PType] =
    models.get(id) orElse enums.get(id)

  def render: String =
    (models.values ++ enums.values).map(displayPType(_, true)).mkString("\n\n")

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
      imports.map(i => i.id -> i).toMap,
      models.map(m => m.id -> m).toMap,
      enums.map(e => e.id -> e).toMap,
      permissions,
      if (config.isEmpty) None else Some(config.head)
    )
  }
}
