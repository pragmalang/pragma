package setup.storage.postgres

import domain._

import org.jooq.DataType
import org.jooq.util.postgres.PostgresDataType

package object utils {
  type IsNotNull = Boolean

  def fieldPostgresType(
      field: PModelField
  )(implicit syntaxTree: SyntaxTree): Option[DataType[_]] =
    field.ptype match {
      case PAny => Some(PostgresDataType.ANY)
      case PString if field.isUUID =>
        Some(PostgresDataType.UUID)
      case PInt if field.isAutoIncrement =>
        Some(PostgresDataType.SERIAL8)
      case PString =>
        Some(PostgresDataType.TEXT)
      case PInt =>
        Some(PostgresDataType.INT8)
      case PFloat =>
        Some(PostgresDataType.FLOAT8)
      case PBool =>
        Some(PostgresDataType.BOOL)
      case PDate =>
        Some(PostgresDataType.DATE)
      case PFile(_, _) =>
        Some(PostgresDataType.TEXT)
      case POption(ptype) => fieldPostgresType(field.copy(ptype = ptype))
      case PReference(id) =>
        fieldPostgresType(syntaxTree.modelsById(id).primaryField)
      case model: PModel =>
        fieldPostgresType(model.primaryField)
      case PEnum(id, values, position) =>
        Some(PostgresDataType.TEXT)
      case PInterface(id, fields, position) => None
      case PArray(ptype)                    => None
      case PFunction(args, returnType)      => None
    }

  def toPostgresType(
      t: PType,
      isOptional: Boolean
  )(implicit syntaxTree: SyntaxTree): Option[DataType[_]] =
    t match {
      case PAny => Some(PostgresDataType.ANY)
      case PEnum(id, values, position) =>
        Some(PostgresDataType.TEXT)
      case PString => Some(PostgresDataType.TEXT)
      case PInt    => Some(PostgresDataType.INT8)
      case PFloat  => Some(PostgresDataType.FLOAT8)
      case PBool   => Some(PostgresDataType.BOOL)
      case PDate   => Some(PostgresDataType.DATE)
      case PFile(sizeInBytes, extensions) =>
        Some(PostgresDataType.TEXT)
      case POption(ptype)                   => toPostgresType(ptype, true)
      case PReference(id)                   => None
      case _: PModel                        => None
      case PInterface(id, fields, position) => None
      case PArray(ptype)                    => None
      case PFunction(args, returnType)      => None
    }
}
