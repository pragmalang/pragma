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

case class RenameModel(prevModelId: String, newId: String)
    extends MigrationStep {
  override def reverse: Option[MigrationStep] =
    Some(RenameModel(newId, prevModelId))
}

case class DeleteModel(prevModel: PModel) extends MigrationStep {
  override def reverse: Option[MigrationStep] = Some(UndeleteModel(prevModel))
}

case class UndeleteModel(prevModel: PModel) extends MigrationStep {
  override def reverse: Option[MigrationStep] = Some(DeleteModel(prevModel))
}

case class AddField(
    field: PModelField,
    prevModel: PModel
) extends MigrationStep {
  override def reverse: Option[MigrationStep] =
    Some(DeleteField(field, prevModel))
}

case class RenameField(
    prevFieldId: String,
    newId: String,
    prevModel: PModel
) extends MigrationStep {
  override def reverse: Option[MigrationStep] =
    Some(RenameField(newId, prevFieldId, prevModel))
}

case class DeleteField(
    prevField: PModelField,
    prevModel: PModel
) extends MigrationStep {
  override def reverse: Option[MigrationStep] =
    Some(UndeleteField(prevField, prevModel))
}

case class UndeleteField(
    prevField: PModelField,
    prevModel: PModel
) extends MigrationStep {
  override def reverse: Option[MigrationStep] =
    Some(DeleteField(prevField, prevModel))
}

case class ChangeManyFieldTypes(
    prevModel: PModel,
    changes: Vector[ChangeFieldType]
) extends MigrationStep {
  override def reverse: Option[MigrationStep] =
    changes.traverse(_.reverse) map { changes =>
      ChangeManyFieldTypes(prevModel, changes)
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
