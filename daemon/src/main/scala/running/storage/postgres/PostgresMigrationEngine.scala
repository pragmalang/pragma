package running.storage.postgres

import running.storage._
import cats._, implicits._
import pragma.domain.utils._
import running.storage.postgres.utils._

import pragma.domain._, DomainImplicits._
import pragma.domain.utils.UserError
import scala.util.Try
import doobie.util.transactor.Transactor
import cats.effect._
import doobie.implicits._
import running.PFunctionExecutor
import running.utils._
import doobie.util.fragment.Fragment

class PostgresMigrationEngine[M[_]: Monad: ConcurrentEffect](
    transactor: Transactor[M],
    currentSyntaxTree: SyntaxTree,
    queryEngine: PostgresQueryEngine[M],
    funcExecutor: PFunctionExecutor[M]
)(
    implicit MError: MonadError[M, Throwable],
    cs: ContextShift[M]
) extends MigrationEngine[Postgres[M], M] {

  override def migrate(mode: Mode, codeToPersist: String): M[Unit] = {

    val createMigrationsTable =
      sql"""
        create table if not exists ___pragma_migrations___ (
          id serial primary key,
          code text not null,
          timestamp timestamp without time zone not null default (now() at time zone 'utc')
        );
        """.update.run.void
        .transact(transactor)

    val prevTreeExists =
      sql"""
          select count(*) from ___pragma_migrations___ limit 1;
        """
        .query[Int]
        .unique
        .transact(transactor)
        .map(_ > 0)
        .handleErrorWith(_ => createMigrationsTable *> false.pure[M])

    val prevTree =
      sql"""
          select code from ___pragma_migrations___
            where id = (select max(id) from ___pragma_migrations___);
          """
        .query[String]
        .unique
        .transact(transactor)
        .map(SyntaxTree.from(_).get)

    val insertMigration =
      sql"""
        insert into ___pragma_migrations___ (code) values ($codeToPersist);
        """.update.run.void.transact(transactor)

    for {
      prevTreeExists <- mode match {
        case Mode.Prod => prevTreeExists
        case Mode.Dev  => false.pure[M]
      }
      prevTree <- if (prevTreeExists) prevTree else SyntaxTree.empty.pure[M]
      thereExistData <- if (prevTreeExists) {
        prevTree.models
          .map { model =>
            Fragment(
              s"select count(*) from ${model.id.withQuotes} limit 1;",
              Nil,
              None
            ).query[Int]
              .stream
              .compile
              .toList
              .map(count => model.id -> (count.head > 0))
          }
          .toVector
          .traverse(d => d.transact(transactor))
          .map(_.toMap)
      } else Map.empty[ModelId, Boolean].pure[M]
      migration <- migration(prevTree, thereExistData)
      _ <- migration.run(transactor)
      _ <- mode match {
        case Mode.Prod if !migration.sqlSteps.isEmpty => insertMigration
        case _                                        => ().pure[M]
      }
    } yield ()
  }

  def migration(
      prevTree: SyntaxTree,
      thereExistData: Map[ModelId, Boolean]
  ): M[PostgresMigration[M]] =
    inferedMigrationSteps(currentSyntaxTree, prevTree, thereExistData) match {
      case Left(err) => MError.raiseError(err)
      case Right(steps) =>
        PostgresMigration[M](
          steps,
          prevTree,
          currentSyntaxTree,
          queryEngine,
          funcExecutor
        ).pure[M]
    }

  private[postgres] def inferedMigrationSteps(
      currentTree: SyntaxTree,
      prevTree: SyntaxTree,
      thereExistData: Map[ModelId, Boolean]
  ): Either[Throwable, Vector[MigrationStep]] =
    Try {
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
          .map(
            indexedModel => DeleteModel(prevTree.modelsById(indexedModel.id))
          )
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
              ChangeFieldType(
                prevField,
                currentField.ptype,
                currentField.directives
                  .find(_.id == "typeTransformer") match {
                  case Some(typeTransformerDir) =>
                    typeTransformerDir.args.value("typeTransformer") match {
                      case func: ExternalFunction => Some(func)
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
                  /*
                    No need for a transformation function, a `NOT NULL` constraint will be
                    added.
                   */
                  case None
                      if `Field type has changed` `from A to A?` (prevField, currentField) =>
                    None
                  /*
                    No need for a transformation function, the current value,
                    will be the first element of the array.
                   */
                  case None
                      if `Field type has changed` `from A to [A]` (prevField, currentField) =>
                    None
                  /*
                      No need for a transformation function, the current value, if not null,
                      will be the first element in the array, and if it's null then the array
                      is empty.
                   */
                  case None
                      if `Field type has changed` `from A? to [A]` (prevField, currentField) =>
                    None
                  case None
                      if thereExistData
                        .withDefault(_ => false)(prevModel.id) => {
                    val requiredFunctionType =
                      s"${displayPType(prevField.ptype)} => ${displayPType(currentField.ptype)}"
                    throw UserError(
                      s"Field `${currentModel}.${currentField}` type has changed, and Pragma needs a transformation function `${requiredFunctionType}` to transform existing data to the new type"
                    )
                  }
                  case _ => None
                }
              ).pure[Vector]
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
    }.toEither
}

/**
  * `IndexedModel#equals` and `IndexedField#equals` assume that the `Validator` has validated
  * that no model or field has the same name in the same scope nor the same index
  */
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
