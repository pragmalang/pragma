package setup.storage.postgres

import domain._
import spray.json._
import domain.utils.InternalException

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
      case PEnum(id, values, position) =>
        Some(PostgresType.TEXT)
      case PInterface(id, fields, position) => None
      case PArray(ptype)                    => None
      case PFunction(args, returnType)      => None
    }

  def toPostgresType(
      t: PType,
      isOptional: Boolean
  )(implicit syntaxTree: SyntaxTree): Option[PostgresType] =
    t match {
      case PAny => Some(PostgresType.ANY)
      case PEnum(id, values, position) =>
        Some(PostgresType.TEXT)
      case PString => Some(PostgresType.TEXT)
      case PInt    => Some(PostgresType.INT8)
      case PFloat  => Some(PostgresType.FLOAT8)
      case PBool   => Some(PostgresType.BOOL)
      case PDate   => Some(PostgresType.DATE)
      case PFile(sizeInBytes, extensions) =>
        Some(PostgresType.TEXT)
      case POption(ptype)                   => toPostgresType(ptype, true)
      case PReference(id)                   => None
      case _: PModel                        => None
      case PInterface(id, fields, position) => None
      case PArray(ptype)                    => None
      case PFunction(args, returnType)      => None
    }

  import sangria.ast._
  def sangriaToJson(v: Value): JsValue = v match {
    case BigDecimalValue(value, _, _)   => JsNumber(value.toDouble)
    case BigIntValue(value, _, _)       => JsNumber(value.toLong)
    case BooleanValue(value, _, _)      => JsBoolean(value)
    case StringValue(value, _, _, _, _) => JsString(value)
    case EnumValue(value, _, _)         => JsString(value)
    case FloatValue(value, _, _)        => JsNumber(value)
    case IntValue(value, _, _)          => JsNumber(value)
    case NullValue(_, _)                => JsNull
    case ListValue(values, _, _)        => JsArray(values.map(sangriaToJson))
    case ObjectValue(fields, _, _) =>
      JsObject(fields.map { field =>
        field.name -> sangriaToJson(field.value)
      }.toMap)
    case VariableValue(name, _, _) =>
      throw InternalException(
        s"Variable $name should have been substituted at query reduction"
      )
  }
}
