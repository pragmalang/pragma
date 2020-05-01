package setup

import domain._
import domain.primitives.PFunctionValue
import spray.json.JsValue

trait MigrationStep {
  def reverse: Option[MigrationStep]
}

case class CreateModel(model: PModel) extends MigrationStep {
  override def reverse: Option[MigrationStep] = Some(DeleteModel(model))
}

case class RenameModel(modelId: String, newId: String) extends MigrationStep {
  override def reverse: Option[MigrationStep] = Some(RenameModel(newId, modelId))
}

case class DeleteModel(model: PModel) extends MigrationStep {
  override def reverse: Option[MigrationStep] = Some(UndeleteModel(model))
}

case class UndeleteModel(model: PModel) extends MigrationStep {
  override def reverse: Option[MigrationStep] = Some(DeleteModel(model))
}

case class AddField(
    field: PModelField,
    model: PModel
) extends MigrationStep {
  override def reverse: Option[MigrationStep] = Some(DeleteField(field, model))
}

case class RenameField(
    fieldId: String,
    newId: String,
    model: PModel
) extends MigrationStep {
  override def reverse: Option[MigrationStep] = Some(RenameField(newId, fieldId, model))
}

case class DeleteField(
    field: PModelField,
    model: PModel
) extends MigrationStep {
  override def reverse: Option[MigrationStep] = Some(UndeleteField(field, model))
}

case class UndeleteField(
    field: PModelField,
    model: PModel
) extends MigrationStep {
  override def reverse: Option[MigrationStep] = Some(DeleteField(field, model))
}

case class ChangeFieldType(
    field: PModelField,
    model: PModel,
    newType: PType,
    transformer: PFunctionValue[JsValue, JsValue]
) extends MigrationStep {
  override def reverse: Option[MigrationStep] = None
}
