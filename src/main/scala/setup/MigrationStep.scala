package setup

import domain._
import spray.json.JsValue
import cats.implicits._

sealed trait MigrationStep {
  def reverse: Option[MigrationStep]
}

case class CreateModel(model: PModel) extends MigrationStep {
  override def reverse: Option[MigrationStep] = Some(DeleteModel(model))
}

case class RenameModel(modelId: String, newId: String) extends MigrationStep {
  override def reverse: Option[MigrationStep] =
    Some(RenameModel(newId, modelId))
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
  override def reverse: Option[MigrationStep] =
    Some(RenameField(newId, fieldId, model))
}

case class DeleteField(
    field: PModelField,
    model: PModel
) extends MigrationStep {
  override def reverse: Option[MigrationStep] =
    Some(UndeleteField(field, model))
}

case class UndeleteField(
    field: PModelField,
    model: PModel
) extends MigrationStep {
  override def reverse: Option[MigrationStep] = Some(DeleteField(field, model))
}

case class ChangeManyFieldTypes(model: PModel, changes: Vector[ChangeFieldType])
    extends MigrationStep {
  override def reverse: Option[MigrationStep] =
    changes.traverse(_.reverse) map { changes =>
      ChangeManyFieldTypes(model, changes)
    }
}

case class ChangeFieldType(
    field: PModelField,
    newType: PType,
    transformer: PFunctionValue[JsValue, JsValue],
    reverseTransformer: Option[PFunctionValue[JsValue, JsValue]]
) {
  def reverse = reverseTransformer map { value =>
    ChangeFieldType(
      field,
      field.ptype,
      value,
      Some(transformer)
    )
  }
}
