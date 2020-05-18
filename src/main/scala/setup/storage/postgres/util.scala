package setup.storage.postgres

import domain._
import Constraint.ColumnConstraint.NotNull

import org.jooq.DataType
import org.jooq.util.postgres.PostgresDataType

package object util {
  type NotNull = NotNull.type

  def toPostgresType(
      t: PType,
      isOptional: Boolean = false
  ): Option[(DataType[_], Option[NotNull])] = {
    val notNull = if (isOptional) None else Some(NotNull)
    val isNotNull = notNull.isDefined
    t match {
      case PString =>
        Some(PostgresDataType.TEXT.nullable(!isNotNull) -> notNull)
      case PInt => Some(PostgresDataType.INT8.nullable(!isNotNull) -> notNull)
      case PFloat =>
        Some(PostgresDataType.FLOAT8.nullable(!isNotNull) -> notNull)
      case PBool => Some(PostgresDataType.BOOL.nullable(!isNotNull) -> notNull)
      case PDate => Some(PostgresDataType.DATE.nullable(!isNotNull) -> notNull)
      case PFile(_, _) =>
        Some(PostgresDataType.TEXT.nullable(!isNotNull) -> notNull)
      case t: POption => toPostgresType(t, true)
      case _          => None
    }
  }

  implicit class StringOptionOps(option: Option[String]) {

    /**
      * Returns the wrapped string in case of `Some` or an empty string in case of `None`
      */
    def unwrapSafe: String = option.getOrElse("")
  }
}
