package running.storage

import pragma.domain._

sealed trait MigrationStep

case class CreateModel(model: PModel) extends MigrationStep

case class RenameModel(prevModelId: String, newId: String) extends MigrationStep

case class DeleteModel(prevModel: PModel) extends MigrationStep

case class UndeleteModel(prevModel: PModel) extends MigrationStep

case class AddField(
    field: PModelField,
    prevModel: PModel
) extends MigrationStep

sealed trait FieldDirectivesChange extends MigrationStep
case class AddDirective(
    prevModel: PModel,
    prevField: PModelField,
    currrentField: PModelField,
    directive: Directive
) extends FieldDirectivesChange

case class DeleteDirective(
    prevModel: PModel,
    prevField: PModelField,
    currrentField: PModelField,
    directive: Directive
) extends FieldDirectivesChange

case class RenameField(
    prevFieldId: String,
    newId: String,
    prevModel: PModel
) extends MigrationStep

case class DeleteField(
    prevField: PModelField,
    prevModel: PModel
) extends MigrationStep

case class UndeleteField(
    prevField: PModelField,
    prevModel: PModel
) extends MigrationStep

case class ChangeManyFieldTypes(
    prevModel: PModel,
    newModel: PModel,
    changes: Vector[ChangeFieldType]
) extends MigrationStep

case class ChangeFieldType(
    prevField: PModelField,
    currrentField: PModelField,
    transformer: Option[PFunctionValue]
)
