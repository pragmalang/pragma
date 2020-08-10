package running.storage.postgres

import running.storage._
import cats._
import domain.SyntaxTree
import domain.utils._

import domain._
import domain.utils.UserError
import scala.util.Try

class PostgresMigrationEngine[M[_]: Monad](syntaxTree: SyntaxTree)
    extends MigrationEngine[Postgres[M], M] {
  implicit val st = syntaxTree

  override def migrate(
      prevTree: SyntaxTree
  ): M[Either[MigrationError, Unit]] = ???

  def initialMigration = migration(SyntaxTree.empty, (_, _) => false)

  def migration(
      prevTree: SyntaxTree = SyntaxTree.empty,
      thereExistData: (ModelId, FieldId) => Boolean
  ): Try[PostgresMigration] =
    inferedMigrationSteps(syntaxTree, prevTree, thereExistData).map(
      PostgresMigration(
        _,
        prevTree,
        syntaxTree
      )
    )

  private[postgres] def inferedMigrationSteps(
      currentTree: SyntaxTree,
      prevTree: SyntaxTree,
      thereExistData: (ModelId, FieldId) => Boolean
  ): Try[Vector[MigrationStep]] = Try {
    if (prevTree.models.isEmpty)
      currentTree.models.map(CreateModel(_)).toVector
    else {
      val currentIndexedModels =
        currentTree.models.map(IndexedModel(_)).toVector
      val prevIndexedModels = prevTree.models.map(IndexedModel(_)).toVector

      val renamedModels = for {
        currentModel <- currentIndexedModels
        prevModel <- prevIndexedModels.find(_.index == currentModel.index)
        if currentModel.id != prevModel.id
      } yield RenameModel(prevModel.id, currentModel.id)

      val newModels = currentIndexedModels
        .filter(
          indexedModel =>
            !prevIndexedModels.exists(_.index == indexedModel.index)
        )
        .map(
          indexedModel => CreateModel(currentTree.modelsById(indexedModel.id))
        )
        .filter(
          createModel =>
            !renamedModels
              .exists(_.newId == createModel.model.id)
        )

      val deletedModels = prevIndexedModels
        .filter(
          indexedModel =>
            !currentIndexedModels.exists(_.index == indexedModel.index)
        )
        .map(indexedModel => DeleteModel(prevTree.modelsById(indexedModel.id)))
        .filter(
          deleteModel =>
            !renamedModels
              .exists(
                _.prevModelId == deleteModel.prevModel.id
              )
        )

      // Models that are not deleted, not renamed, not new, and their fields may have changed
      val unrenamedUndeletedPreviousIndexedModels =
        currentIndexedModels.filter { currentIndexedModel =>
          !renamedModels
            .exists(
              _.prevModelId == prevIndexedModels
                .find(_.index == currentIndexedModel.index)
                .get
                .id
            ) && !deletedModels
            .exists(
              _.prevModel.id == currentIndexedModel.id
            ) && !newModels
            .exists(
              _.model.id == currentIndexedModel.id
            )
        }

      val fieldMigrationSteps = for {
        currentIndexedModel <- unrenamedUndeletedPreviousIndexedModels
        prevIndexedModel <- prevIndexedModels.find(
          _.index == currentIndexedModel.index
        )
        currentModel = currentTree.modelsById(currentIndexedModel.id)
        prevModel = prevTree.modelsById(prevIndexedModel.id)
        newFields = currentIndexedModel.indexedFields
          .filter(
            indexedField =>
              !prevIndexedModel.indexedFields
                .exists(_.index == indexedField.index)
          )
          .map { indexedField =>
            val field = currentModel.fieldsById(indexedField.id)
            AddField(field, prevModel)
          }

        deletedFields = prevIndexedModel.indexedFields
          .filter(
            indexedField =>
              !currentIndexedModel.indexedFields
                .exists(_.index == indexedField.index)
          )
          .map { indexedField =>
            val field = prevModel.fieldsById(indexedField.id)
            DeleteField(field, prevModel)
          }

        renamedFields = for {
          currentIndexedField <- currentIndexedModel.indexedFields
          prevIndexedField <- prevIndexedModel.indexedFields.find(
            _.index == currentIndexedField.index
          )
          if currentIndexedField.id != prevIndexedField.id
        } yield
          RenameField(
            prevIndexedField.id,
            currentIndexedField.id,
            prevModel
          )

        changeTypeFields = (for {
          currentIndexedField <- currentIndexedModel.indexedFields
          prevIndexedField <- prevIndexedModel.indexedFields.find(
            _.index == currentIndexedField.index
          )
          currentField = currentModel.fieldsById(currentIndexedField.id)
          prevField = prevModel.fieldsById(prevIndexedField.id)
          if currentField.ptype != prevField.ptype
          if (prevField.ptype match {
            case _ if prevField.ptype.innerPReference.isDefined =>
              prevField.ptype.innerPReference
                .filter(
                  ref => !renamedModels.exists(_.prevModelId == ref.id)
                )
                .isDefined
            case _ => true
          })
        } yield
          ChangeManyFieldTypes(
            prevModel,
            currentModel,
            Vector(
              ChangeFieldType(
                prevField,
                currentField.ptype,
                currentField.directives.find(_.id == "typeTransformer") match {
                  case Some(typeTransformerDir) =>
                    typeTransformerDir.args.value("typeTransformer") match {
                      case func: ExternalFunction => Some(func)
                      case func: BuiltinFunction  => Some(func)
                      case pvalue => {
                        val found = displayPType(pvalue.ptype)
                        val required =
                          s"${displayPType(prevField.ptype)} => ${displayPType(currentField.ptype)}"
                        throw new InternalException(
                          s"""
                          |Type mismatch on directive `typeTransformer` on field `${currentModel}.${currentField}`
                          |found: `${found}`
                          |required: `${required}`
                          """.tail.stripMargin
                        )
                      }
                    }
                  // If there is no data, then there is no need for a transformation function
                  case None if !thereExistData(prevModel.id, prevField.id) =>
                    None
                  // If from A to A? then there is no need for a transformation function
                  case None
                      if currentField.ptype
                        .isInstanceOf[POption] && (prevField.ptype == currentField.ptype
                        .asInstanceOf[POption]
                        .ptype) =>
                    None
                  // If from A to [A] then there is no need for a transformation function, the current value will be the first element of the array
                  case None
                      if currentField.ptype
                        .isInstanceOf[PArray] && (prevField.ptype == currentField.ptype
                        .asInstanceOf[PArray]
                        .ptype) =>
                    None
                  // If from A? to [A] then there is no need for a transformation function, the current value, if not null,
                  // will be the first element in the array, and if it's null then the array is empty
                  case None
                      if (currentField.ptype
                        .isInstanceOf[PArray] && prevField.ptype
                        .isInstanceOf[POption]) && (prevField.ptype
                        .asInstanceOf[POption]
                        .ptype == currentField.ptype
                        .asInstanceOf[PArray]
                        .ptype) =>
                    None
                  case None if thereExistData(prevModel.id, prevField.id) => {
                    val requiredFunctionType =
                      s"${displayPType(prevField.ptype)} => ${displayPType(currentField.ptype)}"
                    throw UserError(
                      s"Field `${currentModel}.${currentField}` type has changed, and Pragma needs a transformation function `${requiredFunctionType}` to transform existing data to the new type"
                    )
                  }
                  case None => None
                },
                currentField.directives
                  .find(_.id == "reverseTypeTransformer") match {
                  case Some(typeTransformerDir) =>
                    typeTransformerDir.args
                      .value("reverseTypeTransformer") match {
                      case func: ExternalFunction => Some(func)
                      case func: BuiltinFunction  => Some(func)
                      case pvalue => {
                        val found = displayPType(pvalue.ptype)
                        val required =
                          s"${displayPType(currentField.ptype)} => ${displayPType(prevField.ptype)}"
                        throw new InternalException(
                          s"""
                          |Type mismatch on directive `reverseTypeTransformer` on field `${currentModel}.${currentField}`
                          |found: `${found}`
                          |required: `${required}`
                          """.tail.stripMargin
                        )
                      }
                    }
                  case None => None
                }
              )
            )
          )).foldLeft[Option[ChangeManyFieldTypes]](None) {
          case (Some(value), e) if value.prevModel == e.prevModel =>
            Some(
              ChangeManyFieldTypes(
                value.prevModel,
                value.newModel,
                value.changes ++ e.changes
              )
            )
          case (None, e) => Some(e)
          case _         => None
        }

        fieldMigrationSteps: Vector[MigrationStep] = changeTypeFields match {
          case Some(changeTypeFields) =>
            Vector(
              newFields ++ deletedFields ++ renamedFields,
              Vector(changeTypeFields)
            ).flatten
          case None => newFields ++ deletedFields ++ renamedFields
        }
      } yield fieldMigrationSteps

      newModels ++ renamedModels ++ deletedModels ++ fieldMigrationSteps.flatten
    }
  }
}

// `IndexedModel#equals` and `IndexedField#equals` assumes that the `Validator` has validated
// that no model or field has the same name in the same scope nor the same index

case class IndexedModel(
    id: String,
    index: Int,
    indexedFields: Vector[IndexedField]
) {
  override def equals(that: Any): Boolean = that match {
    case that: IndexedModel => index == that.index
    case _                  => false
  }
}
object IndexedModel {
  def apply(model: PModel): IndexedModel =
    IndexedModel(
      model.id,
      model.index,
      model.fields.map(IndexedField(_)).toVector
    )
}
case class IndexedField(
    id: String,
    index: Int,
    directives: Vector[Directive]
) {
  override def equals(that: Any): Boolean = that match {
    case that: IndexedField => index == that.index
    case _                  => false
  }
}
object IndexedField {
  def apply(field: PModelField): IndexedField =
    IndexedField(field.id, field.index, field.directives.toVector)
}
