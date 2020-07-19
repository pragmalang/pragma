package running.storage.postgres

import domain._

package object utils {
  type IsNotNull = Boolean

  def fieldPostgresType(
      field: PModelField
  )(implicit syntaxTree: SyntaxTree): Option[PostgresType] =
    field.ptype match {
      case PAny => Some(PostgresType.ANY)
      case PString if field.isUUID =>
        Some(PostgresType.UUID)
      case PInt if field.isAutoIncrement =>
        Some(PostgresType.SERIAL8)
      case PString =>
        Some(PostgresType.TEXT)
      case PInt =>
        Some(PostgresType.INT8)
      case PFloat =>
        Some(PostgresType.FLOAT8)
      case PBool =>
        Some(PostgresType.BOOL)
      case PDate =>
        Some(PostgresType.DATE)
      case PFile(_, _) =>
        Some(PostgresType.TEXT)
      case POption(ptype) => fieldPostgresType(field.copy(ptype = ptype))
      case PReference(id) =>
        fieldPostgresType(syntaxTree.modelsById(id).primaryField)
      case model: PModel =>
        fieldPostgresType(model.primaryField)
      case PEnum(_, _, _)      => Some(PostgresType.TEXT)
      case PInterface(_, _, _) => None
      case PArray(_)           => None
      case PFunction(_, _)     => None
    }

  def toPostgresType(
      t: PType
  )(implicit syntaxTree: SyntaxTree): Option[PostgresType] =
    t match {
      case PAny                => Some(PostgresType.ANY)
      case PEnum(_, _, _)      => Some(PostgresType.TEXT)
      case PString             => Some(PostgresType.TEXT)
      case PInt                => Some(PostgresType.INT8)
      case PFloat              => Some(PostgresType.FLOAT8)
      case PBool               => Some(PostgresType.BOOL)
      case PDate               => Some(PostgresType.DATE)
      case PFile(_, _)         => Some(PostgresType.TEXT)
      case POption(ptype)      => toPostgresType(ptype)
      case PReference(_)       => None
      case _: PModel           => None
      case PInterface(_, _, _) => None
      case PArray(_)           => None
      case PFunction(_, _)     => None
    }

}
