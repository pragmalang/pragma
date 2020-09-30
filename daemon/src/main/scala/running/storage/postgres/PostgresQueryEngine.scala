package running.storage.postgres

import instances._
import pragma.domain._, pragma.domain.utils._
import pragma.domain.DomainImplicits._
import running.RunningImplicits.PValueJsonWriter
import running._, running.operations._, storage._
import doobie._, doobie.implicits._, doobie.postgres.implicits._
import cats._, cats.implicits._
import cats.effect._
import spray.json._
import com.github.t3hnar.bcrypt._
import scala.util._
import running.utils.QueryError

class PostgresQueryEngine[M[_]: Monad](
    transactor: Transactor[M],
    st: SyntaxTree,
    jc: JwtCodec
)(implicit bracket: Bracket[M, Throwable])
    extends QueryEngine[Postgres[M], M] {
  import PostgresQueryEngine._

  override type Query[A] = PostgresQueryEngine.Query[A]

  override def run(
      operations: Operations.OperationsMap
  ): M[TransactionResultMap] = operations.toVector.traverse {
    case (groupName, opGroup) =>
      opGroup.toVector
        .traverse {
          case (modelSelectionName, modelOps) =>
            modelOps
              .traverse(op => query(op).map(op -> _))
              .map(modelSelectionName -> _)
        }
        .map(groupName -> _)
        .transact(transactor)
  }

  def query(op: Operation): Query[JsValue] = op match {
    case op: ReadOperation =>
      readOneRecord(
        op.targetModel,
        op.opArguments.id,
        op.innerReadOps
      ).widen[JsValue]
    case op: ReadManyOperation =>
      readManyRecords(op.targetModel, op.opArguments.agg, op.innerReadOps)
        .widen[JsValue]
    case op: CreateOperation =>
      createOneRecord(
        op.targetModel,
        op.opArguments.obj,
        op.innerReadOps
      ).widen[JsValue]
    case op: CreateManyOperation =>
      createManyRecords(
        op.targetModel,
        op.opArguments.items.toVector,
        op.innerReadOps
      ).map(JsArray(_))
    case op: PushToOperation =>
      pushOneTo(
        op.targetModel,
        op.arrayField,
        op.opArguments.item,
        op.opArguments.id,
        op.innerReadOps
      ).widen[JsValue]
    case op: PushManyToOperation =>
      pushManyTo(
        op.targetModel,
        op.arrayField,
        op.opArguments.items.toVector,
        op.opArguments.id,
        op.innerReadOps
      ).widen[JsValue]
    case op: DeleteOperation =>
      deleteOneRecord(op.targetModel, op.opArguments.id, op.innerReadOps)
        .widen[JsValue]
    case op: DeleteManyOperation =>
      deleteManyRecords(
        op.targetModel,
        op.opArguments.ids.toVector,
        op.innerReadOps
      ).widen[JsValue]
    case op: RemoveFromOperation =>
      removeOneFrom(
        op.targetModel,
        op.arrayField,
        op.opArguments.id,
        op.opArguments.item,
        op.innerReadOps
      ).widen[JsValue]
    case op: RemoveManyFromOperation =>
      removeManyFrom(
        op.targetModel,
        op.arrayField,
        op.opArguments.id,
        op.opArguments.items,
        op.innerReadOps
      ).widen[JsValue]
    case op: UpdateOperation =>
      updateOneRecord(
        op.targetModel,
        op.opArguments.obj.objId,
        op.opArguments.obj.obj,
        op.innerReadOps
      ).widen[JsValue]
    case op: UpdateManyOperation =>
      updateManyRecords(
        op.targetModel,
        op.opArguments.items.toVector,
        op.innerReadOps
      ).widen[JsValue]
    case op: LoginOperation =>
      login(
        op.targetModel,
        op.opArguments.publicCredentialField,
        op.opArguments.publicCredentialValue,
        op.opArguments.secretCredentialValue
      ).widen[JsValue]
    case otherOp =>
      queryError[JsValue] {
        InternalException(
          s"Unsupported operation of event ${otherOp.event}"
        )
      }
  }

  override def runQuery[A](query: Query[A]): M[A] = query.transact(transactor)

  override def login(
      model: PModel,
      publicCredentialField: PModelField,
      publicCredentialValue: JsValue,
      secretCredentialValue: Option[String]
  ): Query[JsString] =
    model.secretCredentialField zip secretCredentialValue match {
      case Some((scField, scValue)) => {
        val sql =
          s"""|SELECT ${model.primaryField.id.withQuotes}, ${scField.id.withQuotes} FROM ${model.id.withQuotes}
              |WHERE ${publicCredentialField.id.withQuotes} = ? ;
              |""".stripMargin
        val prep = setJsValue(publicCredentialValue, publicCredentialField)
        for {
          resList <- HC.stream(sql, prep, 1).head.compile.toList
          JsObject(fields) = resList.head
          jp = JwtPayload(
            userId = fields(model.primaryField.id),
            role = model.id
          )
          hashed <- fields(scField.id) match {
            case JsString(s) => s.pure[Query]
            case _ =>
              queryError[String] {
                InternalException(
                  "Secret credential value must be a `String`, but retrieved something else from the database"
                )
              }
          }
          token <- scValue.isBcryptedSafeBounded(hashed) match {
            case Success(valid) if valid =>
              JsString(jc.encode(jp)).pure[Query]
            case _ =>
              queryError[JsString](UserError(s"Invalid credentials"))
          }
        } yield token
      }
      case None => {
        val sql =
          s"""|SELECT ${model.primaryField.id.withQuotes} FROM ${model.id.withQuotes}
              |WHERE ${publicCredentialField.id.withQuotes} = ?;""".stripMargin
        val prep = setJsValue(publicCredentialValue, publicCredentialField)

        HC.stream(sql, prep, 1).head.compile.toList.map(_.head).map { resObj =>
          val id = resObj.fields(model.primaryField.id)
          val jp = JwtPayload(
            userId = id,
            role = model.id
          )
          JsString(jc.encode(jp))
        } recoverWith {
          case _ => queryError[JsString](UserError(s"Invalid credentials"))
        }
      }
    }

  override def createManyRecords(
      model: PModel,
      records: Vector[JsObject],
      innerReadOps: Vector[InnerOperation]
  ): Query[Vector[JsObject]] =
    records.traverse(createOneRecord(model, _, innerReadOps))

  override def createOneRecord(
      model: PModel,
      record: JsObject,
      innerReadOps: Vector[InnerOperation]
  ): Query[JsObject] = {
    val fieldTypeMap = fieldTypeMapFrom(record, model, withFieldDefaults = true)

    val refArrayInserts = fieldTypeMap(RefArray)
      .traverse {
        case (
            field @ PModelField(_, PArray(PReference(refId)), _, _, _, _),
            JsArray(rs)
            ) => {
          val refModel = st.modelsById(refId)
          val createdRecords = rs.traverse {
            case r: JsObject => refInsertReturningId(refModel, r)
            case notObj =>
              throw UserError(
                s"Trying to insert non-object value $notObj as a record of type `$refId`"
              )
          }
          createdRecords.map((field, _, refModel.primaryField.id))
        }
        case (otherField, otherVal) =>
          throw InternalException(
            s"Invalid reference array insert of value `${otherVal}` on field `${otherField.id}`"
          )
      }

    val refInserts = fieldTypeMap(Ref)
      .traverse {
        case (field, refRecord: JsObject) => {
          val modelRef = field.ptype match {
            case PReference(id)          => id
            case POption(PReference(id)) => id
            case _ =>
              throw InternalException(
                s"Invalid reference table insert: type `${displayPType(field.ptype)}` is being treated as a reference"
              )
          }
          val refModel = st.modelsById(modelRef)

          refInsertReturningId(refModel, refRecord)
            .map(refModel.primaryField.copy(id = field.id) -> _)
        }
        case (field, JsNull) => (field, JsNull).widen[JsValue].pure[Query]
        case _ =>
          throw InternalException(
            "Trying to insert a non-object value as a referenced object"
          )
      }

    val primFields = refInserts.flatMap { refIns =>
      val withHashedSecretCreds = fieldTypeMap(Prim).traverse {
        case (field, JsString(secret)) if field.isSecretCredential =>
          secret.bcryptSafeBounded.map(field -> JsString(_))
        case otherwise => Success(otherwise)
      }
      withHashedSecretCreds match {
        case Success(primFields) =>
          (primFields ++ refIns).toVector
            .filter(_._2 != JsNull)
            .pure[Query]
        case Failure(_) =>
          queryError[Vector[(PModelField, JsValue)]] {
            UserError(
              s"Could not hash the value of secret credential${model.secretCredentialField
                .map(f => s"`${f.id}`")
                .getOrElse("")}. Secret credential values must consist of 71 or less non-UTF-8 characters"
            )
          }
      }
    }

    val insertedRecordId = for {
      columns <- primFields
      columnSql = columns.map(_._1.id.withQuotes).mkString(", ")
      set = setJsValues(columns)
      valuesSql = List.fill(columns.length)("?").mkString(", ")
      insertSql = s"""|INSERT INTO ${model.id.withQuotes} ($columnSql) 
                      |VALUES ($valuesSql)
                      |RETURNING ${model.primaryField.id.withQuotes};""".stripMargin
      rowId <- HC
        .stream(insertSql, set, 1)
        .head
        .compile
        .toList
        .map(_.head.fields(model.primaryField.id))
        .recoverWith {
          case e: Exception =>
            queryError[JsValue] {
              QueryError(s"Failed to create `${model.id}`: ${e.getMessage}")
            }
        }
    } yield rowId

    for {
      arrays <- refArrayInserts
      id <- insertedRecordId
      _ <- arrays.flatTraverse {
        case (field, values, _) =>
          values.traverse(joinInsert(model, field, id, _))
      }
      _ <- fieldTypeMap(PrimArray).traverse {
        case (field, value) => joinInsert(model, field, id, value)
      }
      created <- readOneRecord(model, id, innerReadOps)
    } yield created
  }

  override def updateManyRecords(
      model: PModel,
      recordsWithIds: Vector[ObjectWithId],
      innerReadOps: Vector[InnerOperation]
  ): Query[JsArray] =
    for {
      newRecords <- recordsWithIds.traverse { obj =>
        updateOneRecord(
          model,
          obj.objId,
          obj.obj,
          innerReadOps
        )
      }
    } yield JsArray(newRecords)

  override def updateOneRecord(
      model: PModel,
      primaryKeyValue: JsValue,
      newRecord: JsObject,
      innerReadOps: Vector[InnerOperation]
  ): Query[JsObject] = {
    val fieldTypeMap =
      fieldTypeMapFrom(newRecord, model, includeNonSpecifiedFields = false)

    val refUpdates: Query[Vector[(PModelField, JsValue)]] = fieldTypeMap(Ref)
      .traverse {
        case (
            field @ PModelField(_, (PReference(modelRef)), _, _, _, _),
            obj: JsObject
            ) => {
          val refModel = st.modelsById(modelRef)
          val refIdIsDefined = obj.fields.contains(refModel.primaryField.id)
          if (refIdIsDefined && obj.fields.knownSize == 1)
            (field, obj.fields(refModel.primaryField.id)).pure[List].pure[Query]
          else if (refIdIsDefined)
            updateOneRecord(
              refModel,
              obj.fields(refModel.primaryField.id),
              obj,
              Vector.empty
            ) *> List.empty[(PModelField, JsValue)].pure[Query]
          else
            for {
              refId <- HC
                .stream(
                  s"SELECT ${field.id.withQuotes} FROM ${model.id.withQuotes} WHERE ${model.primaryField.id.withQuotes} = ?;",
                  setJsValue(primaryKeyValue, refModel.primaryField),
                  1
                )
                .head
                .compile
                .toList
                .map(_.head.fields(field.id))
              _ <- updateOneRecord(refModel, refId, obj, Vector.empty)
            } yield List.empty[(PModelField, JsValue)]
        }
        case _ =>
          queryError[List[(PModelField, JsValue)]] {
            InternalException("Invalid reference field update")
          }
      }
      .map(_.flatten)

    val refArrayUpdates = fieldTypeMap(RefArray).traverse {
      case (
          PModelField(_, PArray(PReference(modelRef)), _, _, _, _),
          JsArray(values)
          ) if st.modelsById.contains(modelRef) => {
        val refModel = st.modelsById(modelRef)
        values.traverse {
          case obj: JsObject =>
            updateOneRecord(
              refModel,
              obj.fields(refModel.primaryField.id),
              obj,
              Vector.empty
            )
          case otherVal =>
            queryError[JsObject] {
              UserError(
                s"Invalid value `$otherVal` in nested UPDATE_MANY `${refModel.id}` operation"
              )
            }
        }
      }
      case _ => Vector.empty[JsObject].pure[ConnectionIO]
    }

    val primArrayUpdates = fieldTypeMap(PrimArray).traverse {
      case (
          arrayField @ PModelField(_, PArray(refEnum: PEnum), _, _, _, _),
          JsArray(values)
          ) =>
        values
          .collectFirst {
            case JsString(value) if !refEnum.values.contains(value) =>
              queryError[JsObject] {
                UserError(s"Value `$value` is not a member of `${refEnum.id}`")
              }
            case _: JsNumber | JsNull | _: JsObject | _: JsArray =>
              queryError[JsObject] {
                UserError("Illegal non-string value in enum array field update")
              }
          }
          .getOrElse {
            deleteAllJoinRecords(model, arrayField, primaryKeyValue) *>
              pushManyTo(
                model,
                arrayField,
                values,
                primaryKeyValue,
                Vector.empty
              )
          }
          .widen[JsValue]
      case (arrayField, JsArray(values)) =>
        deleteAllJoinRecords(model, arrayField, primaryKeyValue) *>
          pushManyTo(model, arrayField, values, primaryKeyValue, Vector.empty)
            .widen[JsValue]
      case _ =>
        queryError[JsValue] {
          InternalException(" Primitive array field should have an array value")
        }
    }

    val primUpdate = for {
      primColumns <- refUpdates.map(fieldTypeMap(Prim) ++ _)
      primUpdateSql = s"UPDATE ${model.id.withQuotes} SET " +
        primColumns
          .map {
            case (field, _) => s"${field.id.withQuotes} = ?"
          }
          .mkString(", ") +
        s" WHERE ${model.primaryField.id.withQuotes} = ?;"
      primUpdatePrep = setJsValues(primColumns) *>
        setJsValue(
          primaryKeyValue,
          model.primaryField,
          fieldTypeMap(Prim).length + 1
        )
      newId <- HC
        .updateWithGeneratedKeys(model.primaryField.id :: Nil)(
          primUpdateSql,
          primUpdatePrep,
          1
        )
        .head
        .compile
        .toList
        .map(_.head.fields(model.primaryField.id))
    } yield newId

    for {
      _ <- refArrayUpdates
      _ <- primArrayUpdates
      newId <- primUpdate
      newRecord <- readOneRecord(model, newId, innerReadOps)
    } yield newRecord
  }

  override def deleteManyRecords(
      model: PModel,
      primaryKeyValues: Vector[JsValue],
      innerReadOps: Vector[InnerOperation]
  ): Query[JsArray] =
    primaryKeyValues
      .traverse(deleteOneRecord(model, _, innerReadOps))
      .map(JsArray(_))

  override def deleteOneRecord(
      model: PModel,
      primaryKeyValue: JsValue,
      innerReadOps: Vector[InnerOperation],
      cascade: Boolean = false
  ): Query[JsObject] =
    if (cascade) cascadeDelete(model, primaryKeyValue, innerReadOps)
    else strictDelete(model, primaryKeyValue, innerReadOps)

  private def strictDelete(
      model: PModel,
      id: JsValue,
      innerOps: Vector[InnerOperation]
  ): Query[JsObject] = {
    val toDelete = readOneRecord(model, id, innerOps)
    val sql =
      s"DELETE FROM ${model.id.withQuotes} WHERE ${model.primaryField.id.withQuotes} = ?;"
    toDelete <* HC
      .updateWithGeneratedKeys(Nil)(sql, setJsValue(id, model.primaryField), 0)
      .compile
      .drain
  }

  private def cascadeDelete(
      model: PModel,
      id: JsValue,
      innerOps: Vector[InnerOperation]
  ): Query[JsObject] = {
    val fieldKindMap = modelFieldKindMap(model.id)
    for {
      record <- selectAllById(model, id)
      _ <- fieldKindMap(Ref).toList.traverse { field =>
        val refModel = field.ptype match {
          case PReference(id)          => st.modelsById(id)
          case POption(PReference(id)) => st.modelsById(id)
          case _                       => ???
        }
        deleteOneRecord(
          refModel,
          record.fields.get(field.id).getOrElse(JsNull),
          Vector.empty
        )
      }
      toDelete <- strictDelete(model, id, innerOps)
    } yield toDelete
  }

  override def pushManyTo(
      model: PModel,
      field: PModelField,
      items: Vector[JsValue],
      primaryKeyValue: JsValue,
      innerReadOps: Vector[InnerOperation]
  ): Query[JsArray] =
    items
      .traverse { item =>
        pushOneTo(model, field, item, primaryKeyValue, innerReadOps)
      }
      .map(JsArray(_))

  override def pushOneTo(
      model: PModel,
      field: PModelField,
      item: JsValue,
      sourceId: JsValue,
      innerReadOps: Vector[InnerOperation]
  ): Query[JsValue] = (field.ptype, item) match {
    case (PArray(PReference(refId)), obj: JsObject) => {
      val refModel = st.modelsById(refId)
      refInsertReturningId(refModel, obj).flatMap { id =>
        joinInsert(model, field, sourceId, id) *>
          readOneRecord(refModel, id, innerReadOps).widen[JsValue]
      }
    }
    case (PArray(_), value) =>
      joinInsert(model, field, sourceId, value) *> value.pure[Query]
    case _ =>
      throw InternalException(
        s"Invalid operation PUSH_TO with value $item to field ${field.id}"
      )
  }

  override def removeManyFrom(
      model: PModel,
      arrayField: PModelField,
      sourcePkValue: JsValue,
      targetPkValues: Vector[JsValue],
      innerReadOps: Vector[InnerOperation]
  ): Query[JsArray] =
    targetPkValues
      .traverse { targetPk =>
        removeOneFrom(model, arrayField, sourcePkValue, targetPk, innerReadOps)
      }
      .map(JsArray(_))

  override def removeOneFrom(
      model: PModel,
      arrayField: PModelField,
      sourcePkValue: JsValue,
      targetValue: JsValue,
      innerReadOps: Vector[InnerOperation]
  ): Query[JsValue] = {
    val (targetColumn, refModel) = arrayField.ptype match {
      case PArray(PReference(refId)) =>
        (("target_" + refId).withQuotes, st.modelsById.get(refId))
      case _ => ("target", None)
    }
    val sql =
      s"DELETE FROM ${model.id.concat("_" + arrayField.id).withQuotes} WHERE ${"source_"
        .concat(model.id)
        .withQuotes} = ? AND $targetColumn = ?;"
    val prep = setJsValue(sourcePkValue, model.primaryField, 1) *>
      setJsValue(
        targetValue,
        refModel.map(_.primaryField).getOrElse(arrayField),
        2
      )
    HC.updateWithGeneratedKeys(Nil)(sql, prep, 0).compile.drain *> {
      refModel match {
        case None => targetValue.pure[Query]
        case Some(m) =>
          readOneRecord(m, targetValue, innerReadOps).widen[JsValue]
      }
    }
  }

  override def readManyRecords(
      model: PModel,
      agg: ModelAgg,
      innerReadOps: Vector[InnerOperation]
  ): Query[JsArray] = {
    val columns =
      commaSepColumnNames(Operations.primaryFieldInnerOp(model) +: innerReadOps)

    val (aggStr, aggSet, _, usedTables) = QueryAggSqlGen.modelAggSql(agg)

    val tableNames = (usedTables + model.id.withQuotes).mkString(", ")

    val sql =
      s"SELECT $columns FROM $tableNames ${if (aggStr.isEmpty) ""
      else "WHERE " + aggStr};"

    HC.stream(sql, aggSet, 520)
      .compile
      .toVector
      .flatMap(_.traverse(populateObject(model, _, innerReadOps)))
      .map(objects => JsArray(objects))
  }

  override def readOneRecord(
      model: PModel,
      primaryKeyValue: JsValue,
      innerReadOps: Vector[InnerOperation]
  ): Query[JsObject] = {
    val innerOpsWithPK = innerReadOps :+ Operations.primaryFieldInnerOp(model)
    val aliasedColumns = commaSepColumnNames(innerOpsWithPK)

    val sql =
      s"""SELECT $aliasedColumns FROM ${model.id.withQuotes} WHERE ${model.id.withQuotes}.${model.primaryField.id.withQuotes} = ?;"""

    val prep = setJsValue(primaryKeyValue, model.primaryField)

    HC.stream(sql, prep, 1)
      .head
      .compile
      .toList
      .map(
        _.headOption.getOrElse(
          throw new Exception(
            s"${model.id} of ID $primaryKeyValue does not exist"
          )
        )
      )
      .flatMap(populateObject(model, _, innerReadOps))
  }

  private def populateObject(
      model: PModel,
      unpopulated: JsObject,
      innerOps: Vector[InnerOperation]
  ): Query[JsObject] = {
    val newObjFields: Query[Vector[(String, JsValue)]] = innerOps
      .map(iop => iop -> iop.targetField.field)
      .traverse {
        case (iop, PModelField(_, PReference(_), _, _, _, _)) =>
          populateId(
            iop.targetModel,
            unpopulated.fields(iop.targetField.field.id),
            iop.innerReadOps
          ).map(obj => iop.targetField.field.id -> obj)
        case (
            iop: InnerReadManyOperation,
            arrayField @ PModelField(_, PArray(_), _, _, _, _)
            ) =>
          populateArray(
            model,
            unpopulated.fields(model.primaryField.id),
            arrayField,
            iop
          ).map(vec => iop.targetField.field.id -> JsArray(vec))
        case (
            iop,
            PModelField(_, POption(PReference(_)), _, _, _, _)
            ) =>
          populateId(
            iop.targetModel,
            unpopulated.fields(iop.nameOrAlias),
            iop.innerReadOps
          ).map(obj => iop.nameOrAlias -> obj)
            .recover {
              case _ => iop.targetField.field.id -> JsNull
            }
        case (iop, _) =>
          Monad[Query].pure {
            iop.nameOrAlias -> unpopulated.fields(iop.targetField.field.id)
          }
      }

    newObjFields.map { nfields =>
      JsObject(unpopulated.fields ++ nfields)
    }
  }

  private def populateId(
      model: PModel,
      key: JsValue,
      iops: Vector[InnerOperation]
  ): Query[JsValue] =
    key match {
      case JsNull => JsNull.pure[Query].widen[JsValue]
      case v      => readOneRecord(model, v, iops).widen[JsValue]
    }

  private def populateArray(
      baseModel: PModel,
      baseRecordId: JsValue,
      arrayField: PModelField,
      arrayInnerOp: InnerReadManyOperation
  ): Query[Vector[JsValue]] = {
    val arrayTableName = baseModel.id
      .concat("_")
      .concat(arrayInnerOp.targetField.field.id)
      .withQuotes
    val (sql, prep) = arrayInnerOp.opArguments.agg match {
      case None => {
        val str =
          s"""|SELECT ${("target_" + arrayInnerOp.targetModel.id).withQuotes}
              |FROM $arrayTableName
              |WHERE ${("source_" + baseModel.id).withQuotes} = ?;
           """.stripMargin
        val set = setJsValue(baseRecordId, baseModel.primaryField)
        (str, set)
      }
      case Some(agg) => {
        val (aggStr, aggSet, _, aggUsedTables) =
          QueryAggSqlGen.arrayFieldAggSql(agg, 2)
        val tableNames = aggUsedTables.incl(arrayTableName).mkString(", ")
        val str =
          s"""|SELECT ${("target_" + arrayInnerOp.targetModel.id).withQuotes}
              |FROM $tableNames
              |WHERE ${("source_" + baseModel.id).withQuotes} = ?
              |${if (aggStr.isEmpty) ";" else s"AND $aggSet;"}
          """.stripMargin
        val set = setJsValue(baseRecordId, baseModel.primaryField) *> aggSet
        (str, set)
      }
    }

    val joinRecords = HC.stream(sql, prep, 200)

    arrayField.ptype match {
      case PArray(PReference(ref)) if st.modelsById.contains(ref) =>
        joinRecords
          .map { obj =>
            populateId(
              st.modelsById(ref),
              obj.fields(obj.fields.head._1),
              arrayInnerOp.innerReadOps
            )
          }
          .compile
          .toVector
          .flatMap(v => v.sequence[Query, JsValue])
      case _ => joinRecords.map(_.fields.head._2).compile.toVector
    }
  }

  private def refInsertReturningId(refModel: PModel, value: JsObject) =
    // If the user is referring to an existing record
    if (value.fields.size == 1 && value.fields.head._1 == refModel.primaryField.id)
      value.fields(refModel.primaryField.id).pure[Query]
    else
      createOneRecord(
        refModel,
        value,
        Vector(Operations.primaryFieldInnerOp(refModel))
      ).map(_.fields(refModel.primaryField.id))

  /** Use to get the kind of a field, paired to the field and its value in the record */
  private def fieldTypeMapFrom(
      record: JsObject,
      recordModel: PModel,
      withFieldDefaults: Boolean = false,
      includeNonSpecifiedFields: Boolean = true
  ): FieldKindValueMap = {
    val fieldKindMap = modelFieldKindMap(recordModel.id)
    fieldKindMap
      .map {
        case (kind, fields) =>
          kind -> fields.collect {
            case field if record.fields.contains(field.id) =>
              (field, record.fields(field.id))
            case field @ PModelField(_, _, Some(default), _, _, _)
                if withFieldDefaults =>
              (field, PValueJsonWriter.write(default))
            case field if includeNonSpecifiedFields => (field, JsNull)
          }.toVector
      }
      .withDefaultValue(Vector.empty)
  }

  /** Use to get a model's fields by their `FieldKind` */
  private val modelFieldKindMap: FieldKindMap =
    st.models.map { model =>
      model.id -> model.fields
        .groupBy {
          case PModelField(_, PReference(refId), _, _, _, _) =>
            if (st.modelsById.contains(refId)) Ref
            else Prim
          case PModelField(_, PArray(PReference(refId)), _, _, _, _) =>
            if (st.modelsById.contains(refId)) RefArray
            else PrimArray
          case PModelField(_, POption(PReference(refId)), _, _, _, _) =>
            if (st.modelsById.contains(refId)) Ref
            else Prim
          case PModelField(_, PArray(_), _, _, _, _) => PrimArray
          case _                                     => Prim
        }
        .withDefaultValue(Vector.empty)
    }.toMap

  private def joinInsert(
      model: PModel,
      field: PModelField,
      sourceValue: JsValue,
      targetValue: JsValue
  ): Query[Unit] = {
    val joinTable = model.id.concat("_").concat(field.id).withQuotes
    val sourceField = model.id.concat("_").concat(field.id).withQuotes
    val sql =
      s"INSERT INTO $joinTable VALUES (?, ?) RETURNING $sourceField;"
    val targetPkField = field.ptype match {
      case PArray(PReference(id)) => st.modelsById(id).primaryField
      case _                      => field
    }
    val set = setJsValue(sourceValue, model.primaryField) *>
      setJsValue(targetValue, targetPkField, 2)
    HC.stream(sql, set, 1).compile.toVector.as(())
  }

}
object PostgresQueryEngine {
  type Query[A] = ConnectionIO[A]

  /** To be used in `INSERT` statement classification */
  private sealed trait FieldKind
  private object Ref extends FieldKind
  private object Prim extends FieldKind
  private object RefArray extends FieldKind
  private object PrimArray extends FieldKind

  private type FieldKindMap = Map[ModelId, Map[FieldKind, Seq[PModelField]]]

  private type FieldKindValueMap =
    Map[FieldKind, Vector[(PModelField, JsValue)]]

  /** Utility function to get a `PreparedStatementIO`
    * from a JSON value.
    */
  private def setJsValue(
      jsVal: JsValue,
      modelField: PModelField,
      paramIndex: Int = 1
  ) = jsVal match {
    case JsString(s) if modelField.isUUID =>
      HPS.set(paramIndex, java.util.UUID.fromString(s))
    case JsString(s)  => HPS.set(paramIndex, s)
    case JsNumber(n)  => HPS.set(paramIndex, n.toDouble)
    case JsBoolean(b) => HPS.set(paramIndex, b)
    case JsNull       => setJsNull(paramIndex, modelField.ptype)
    case _ =>
      throw InternalException(
        s"Trying to set illegal value $jsVal at index $paramIndex in SQL query"
      )
  }

  private def setJsNull(
      paramIndex: Int,
      ptype: PType
  ): PreparedStatementIO[Unit] = ptype match {
    case POption(t) => setJsNull(paramIndex, t)
    case _: PEnum   => HPS.set(paramIndex, Option.empty[String])
    case PString    => HPS.set(paramIndex, Option.empty[String])
    case PInt       => HPS.set(paramIndex, Option.empty[Long])
    case PFloat     => HPS.set(paramIndex, Option.empty[Double])
    case PBool      => HPS.set(paramIndex, Option.empty[Boolean])
    case PDate      => HPS.set(paramIndex, Option.empty[java.util.Date])
    case _ =>
      throw new InternalException(
        s"Trying to set column value of type `${displayPType(ptype)}` to NULL"
      )
  }

  /** Sets all the given values based on their index in the sequence */
  private def setJsValues(
      values: Seq[(PModelField, JsValue)]
  ): PreparedStatementIO[Unit] =
    values.zipWithIndex.foldLeft(HPS.set(())) {
      case (set, ((field, value), index)) =>
        set *> setJsValue(value, field, index + 1)
    }

  private def commaSepColumnNames(
      innerReadOps: Vector[InnerOperation]
  ): String =
    innerReadOps
      .filter {
        case _: InnerReadOperation => true
        case _                     => false
      }
      .map(_.targetField.field.id.withQuotes)
      .mkString(", ")

  private def selectAllById(model: PModel, id: JsValue): Query[JsObject] = {
    val sql =
      s"SELECT * FROM ${model.id.withQuotes} WHERE ${model.primaryField.id} = ?;"
    HC.stream(sql, setJsValue(id, model.primaryField), 1)
      .head
      .compile
      .toList
      .map(_.head)
  }

  /** Returns a `Query` resulting in the given error */
  private def queryError[Result](err: Throwable): Query[Result] =
    MonadError[Query, Throwable].raiseError[Result](err)

  /** Deletes all records in the array field join table
    * where the source equals `sourcePkValue`
    */
  private def deleteAllJoinRecords(
      sourceModel: PModel,
      arrayField: PModelField,
      sourcePkVlaue: JsValue
  ): Query[Unit] = {
    val sql =
      s"DELETE FROM ${sourceModel.id.concat("_").concat(arrayField.id).withQuotes} WHERE ${"source_".concat(sourceModel.id).withQuotes} = ?;"
    val prep = setJsValue(sourcePkVlaue, sourceModel.primaryField)
    HC.updateWithGeneratedKeys(Nil)(sql, prep, 0).compile.drain
  }

}
