package running.storage

import domain._
import spray.json.JsValue
import scala.util.Try

sealed trait MigrationStep {
  def reverse: MigrationStep
}

case class CreateModel(model: PModel) extends MigrationStep {
  override def reverse: MigrationStep = DeleteModel(model)
}

case class RenameModel(prevModelId: String, newId: String)
    extends MigrationStep {
  override def reverse: MigrationStep =
    RenameModel(newId, prevModelId)
}

case class DeleteModel(prevModel: PModel) extends MigrationStep {
  override def reverse: MigrationStep = UndeleteModel(prevModel)
}

case class UndeleteModel(prevModel: PModel) extends MigrationStep {
  override def reverse: MigrationStep = DeleteModel(prevModel)
}

case class AddField(
    field: PModelField,
    prevModel: PModel
) extends MigrationStep {
  override def reverse: MigrationStep =
    DeleteField(field, prevModel)
}

case class RenameField(
    prevFieldId: String,
    newId: String,
    prevModel: PModel
) extends MigrationStep {
  override def reverse: MigrationStep =
    RenameField(newId, prevFieldId, prevModel)
}

case class DeleteField(
    prevField: PModelField,
    prevModel: PModel
) extends MigrationStep {
  override def reverse: MigrationStep =
    UndeleteField(prevField, prevModel)
}

case class UndeleteField(
    prevField: PModelField,
    prevModel: PModel
) extends MigrationStep {
  override def reverse: MigrationStep =
    DeleteField(prevField, prevModel)
}

case class ChangeManyFieldTypes(
    prevModel: PModel,
    newModel: PModel,
    changes: Vector[ChangeFieldType]
) extends MigrationStep {
  override def reverse: MigrationStep =
    ChangeManyFieldTypes(newModel, prevModel, changes.map(_.reverse))
}

case class ChangeFieldType(
    field: PModelField,
    newType: PType,
    transformer: Option[PFunctionValue[JsValue, Try[JsValue]]],
    reverseTransformer: Option[PFunctionValue[JsValue, Try[JsValue]]]
) {
  def reverse: ChangeFieldType =
    ChangeFieldType(
      field,
      field.ptype,
      reverseTransformer,
      transformer
    )
}
