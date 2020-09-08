package running.storage.postgres

import domain._
import domain.utils.InternalException
import running.storage.postgres.OnDeleteAction.Cascade
import running.storage.postgres.SQLMigrationStep.CreateTable
import cats.implicits._

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
  ): Option[PostgresType] =
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

  @throws[InternalException]
  def createArrayFieldTable(
      model: PModel,
      field: PModelField,
      currentSyntaxTree: SyntaxTree
  ) = field.ptype match {
    case POption(PArray(_)) | PArray(_) =>
      Some {
        val tableMetadata = new ArrayFieldTableMetaData(model, field)

        val innerRefType: Option[PModel] = field.innerModelId
          .flatMap(name => currentSyntaxTree.modelsById.get(name))

        val thisModelReferenceColumn = ColumnDefinition(
          tableMetadata.sourceColumnName,
          model.primaryField.ptype match {
            case PString if model.primaryField.isUUID => PostgresType.UUID
            case PString                              => PostgresType.TEXT
            case PInt                                 => PostgresType.INT8
            case t =>
              throw new InternalException(
                s"Primary field in model `${model.id}` has type `${domain.utils.displayPType(t)}` and primary fields can only be of type `Int` or type `String`. This error is unexpected and must be reviewed by the creators of Pragma."
              )
          },
          isNotNull = true,
          isAutoIncrement = false,
          isPrimaryKey = false,
          isUUID = false,
          isUnique = false,
          foreignKey = ForeignKey(
            model.id,
            model.primaryField.id,
            onDelete = Cascade
          ).some
        )

        val valueOrReferenceColumn = innerRefType match {
          case Some(otherModel) =>
            ColumnDefinition(
              name = tableMetadata.targetColumnName,
              dataType = otherModel.primaryField.ptype match {
                case PString if otherModel.primaryField.isUUID =>
                  PostgresType.UUID
                case PString => PostgresType.TEXT
                case PInt    => PostgresType.INT8
                case t =>
                  throw new InternalException(
                    s"Primary field in model `${otherModel.id}` has type `${domain.utils.displayPType(t)}` and primary fields can only be of type `Int` or type `String`. This error is unexpected and must be reviewed by the creators of Pragma."
                  )
              },
              isNotNull = true,
              isAutoIncrement = false,
              isPrimaryKey = false,
              isUUID = false,
              isUnique = false,
              foreignKey = ForeignKey(
                otherModel.id,
                otherModel.primaryField.id,
                onDelete = Cascade
              ).some
            )
          case None =>
            ColumnDefinition(
              name = tableMetadata.targetColumnName,
              dataType = toPostgresType(field.ptype).get,
              isNotNull = true,
              isAutoIncrement = false,
              isPrimaryKey = false,
              isUUID = false,
              isUnique = false,
              foreignKey = None
            )
        }

        val columns =
          Vector(thisModelReferenceColumn, valueOrReferenceColumn)

        CreateTable(tableMetadata.tableName, columns)
      }
    case _ => None
  }

  object `Field type has changed` {
    def `from A to A?`(
        prevField: PModelField,
        currentField: PModelField
    ): Boolean =
      currentField.ptype
        .isInstanceOf[POption] && (prevField.ptype == currentField.ptype
        .asInstanceOf[POption]
        .ptype)

    def `from A to A?`(
        prevFieldType: PType,
        currentFieldType: PType
    ): Boolean =
      currentFieldType
        .isInstanceOf[POption] && (prevFieldType == currentFieldType
        .asInstanceOf[POption]
        .ptype)

    def `from A to [A]`(
        prevField: PModelField,
        currentField: PModelField
    ): Boolean =
      currentField.ptype
        .isInstanceOf[PArray] && (prevField.ptype == currentField.ptype
        .asInstanceOf[PArray]
        .ptype)

    def `from A to [A]`(
        prevFieldType: PType,
        currentFieldType: PType
    ): Boolean =
      currentFieldType
        .isInstanceOf[PArray] && (prevFieldType == currentFieldType
        .asInstanceOf[PArray]
        .ptype)

    def `from A? to [A]`(
        prevField: PModelField,
        currentField: PModelField
    ): Boolean =
      (currentField.ptype
        .isInstanceOf[PArray] && prevField.ptype
        .isInstanceOf[POption]) && (prevField.ptype
        .asInstanceOf[POption]
        .ptype == currentField.ptype
        .asInstanceOf[PArray]
        .ptype)

    def `from A? to [A]`(
        prevFieldType: PType,
        currentFieldType: PType
    ): Boolean =
      (currentFieldType
        .isInstanceOf[PArray] && prevFieldType
        .isInstanceOf[POption]) && (prevFieldType
        .asInstanceOf[POption]
        .ptype == currentFieldType
        .asInstanceOf[PArray]
        .ptype)
  }

}

class ArrayFieldTableMetaData(model: PModel, field: PModelField) {
  val tableName = s"${model.id}_${field.id}"
  val sourceColumnName = s"source_${model.id}"
  val targetColumnName = field.innerModelId match {
    case Some(otherModel) => s"target_${otherModel}"
    case None             => field.id
  }
  val targetColumnIsReference = field.innerModelId.isDefined
}
