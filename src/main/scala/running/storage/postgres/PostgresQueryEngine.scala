package running.storage.postgres

import domain._, domain.utils._
import running._, storage._
import doobie._
import doobie.implicits._
import cats._
import cats.implicits._
import cats.effect._
import spray.json._
import domain.DomainImplicits._

class PostgresQueryEngine[M[_]: Monad](
    transactor: Transactor[M],
    st: SyntaxTree
)(implicit bracket: Bracket[M, Throwable])
    extends QueryEngine[Postgres[M], M] {
  import PostgresQueryEngine._

  override type Query[A] = PostgresQueryEngine.Query[A]

  override def run(operations: Operations.OperationsMap): M[JsObject] = {
    val results = operations.toVector
      .flatMap { pair =>
        pair._2.map {
          case op: ReadOperation => {
            val record = readOneRecord(
              op.targetModel,
              op.opArguments.id,
              op.innerReadOps
            )
            (pair._1.getOrElse("data") -> record.widen[JsValue]).sequence
          }
          case op: ReadManyOperation => {
            val where = QueryWhere(None, None, None)
            val records =
              readManyRecords(op.targetModel, where, op.innerReadOps)
            (pair._1.getOrElse("data") -> records.widen[JsValue]).sequence
          }
          case op: CreateOperation => {
            val result = createOneRecord(
              op.targetModel,
              op.opArguments.obj,
              op.innerReadOps
            )
            (pair._1.getOrElse("data") -> result.widen[JsValue]).sequence
          }
          case op: CreateManyOperation => {
            val created =
              createManyRecords(
                op.targetModel,
                op.opArguments.items.toVector,
                op.innerReadOps
              ).map(JsArray(_))
            (pair._1.getOrElse("data") -> created.widen[JsValue]).sequence
          }
          case op: PushToOperation =>
            pushOneTo(
              op.targetModel,
              op.arrayField,
              op.opArguments.item,
              op.opArguments.id,
              op.innerReadOps
            ).widen[JsValue].map(pair._1.getOrElse("data") -> _)
          case op: PushManyToOperation =>
            pushManyTo(
              op.targetModel,
              op.arrayField,
              op.opArguments.items.toVector,
              op.opArguments.id,
              op.innerReadOps
            ).widen[JsValue].map(pair._1.getOrElse("data") -> _)

          case op: DeleteOperation =>
            deleteOneRecord(op.targetModel, op.opArguments.id, op.innerReadOps)
              .widen[JsValue]
              .map(pair._1.getOrElse("data") -> _)
          case op: DeleteManyOperation =>
            deleteManyRecords(
              op.targetModel,
              op.opArguments.ids.toVector,
              op.innerReadOps
            ).widen[JsValue]
              .map(pair._1.getOrElse("data") -> _)
          case op: RemoveFromOperation =>
            removeOneFrom(
              op.targetModel,
              op.arrayField,
              op.opArguments.id,
              op.opArguments.item,
              op.innerReadOps
            ).widen[JsValue].map(pair._1.getOrElse("data") -> _)
          case op: RemoveManyFromOperation =>
            removeManyFrom(
              op.targetModel,
              op.arrayField,
              op.opArguments.id,
              op.opArguments.items,
              op.innerReadOps
            ).widen[JsValue].map(pair._1.getOrElse("data") -> _)
          case _: UpdateOperation     => ???
          case _: UpdateManyOperation => ???
          case otherOp =>
            throw InternalException(
              s"Unsupported operation of event ${otherOp.event}"
            )
        }
      }
      .sequence
      .map(fields => JsObject(fields.toMap))

    results.transact(transactor)
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
  ): ConnectionIO[JsObject] = {
    val fieldTypeMap = fieldTypeMapFrom(record, model)

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
        case _ => throw InternalException("Invalid reference array insert")
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

          refInsertReturningId(refModel, refRecord).map(field -> _)
        }
        case (field, JsNull) => (field, JsNull).widen[JsValue].pure[Query]
        case _ =>
          throw InternalException(
            "Trying to insert a non-object value as a referenced object"
          )
      }

    val primFields = refInserts.map(fieldTypeMap(Prim) ++ _)

    val insertedRecordId = for {
      columns <- primFields
      columnSql = columns.map(_._1.id.withQuotes).mkString(", ")
      set = columns.zipWithIndex.foldLeft(HPS.set(())) {
        case (acc, ((_, value), index)) =>
          acc *> setJsValue(value, index + 1)
      }
      insertSql = s"INSERT INTO ${model.id.withQuotes} (${columnSql}) VALUES (" +
        List.fill(columns.length)("?").mkString(", ") +
        s") RETURNING ${model.primaryField.id.withQuotes};"
      rowId <- HC
        .stream(insertSql, set, 1)
        .head
        .compile
        .toList
        .map(_.head.fields(model.primaryField.id))
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
      recordsWithIds: Vector[JsObject],
      innerReadOps: Vector[InnerOperation]
  ): Query[JsArray] = ???

  override def updateOneRecord(
      model: PModel,
      primaryKeyValue: Either[Long, String],
      newRecord: JsObject,
      innerReadOps: Vector[InnerOperation]
  ): Query[JsObject] = ???

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
      .updateWithGeneratedKeys(Nil)(sql, setJsValue(id), 0)
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
      field: PShapeField,
      items: Vector[JsValue],
      primaryKeyValue: JsValue,
      innerReadOps: Vector[InnerOperation]
  ): Query[JsObject] =
    for {
      _ <- items.traverse { item =>
        pushOneTo(model, field, item, primaryKeyValue, Vector.empty)
      }
      selected <- readOneRecord(model, primaryKeyValue, innerReadOps)
    } yield selected

  override def pushOneTo(
      model: PModel,
      field: PShapeField,
      item: JsValue,
      sourceId: JsValue,
      innerReadOps: Vector[InnerOperation]
  ): Query[JsObject] = {
    val insert = (field.ptype, item) match {
      case (PArray(PReference(id)), obj: JsObject) =>
        refInsertReturningId(st.modelsById(id), obj).flatMap { id =>
          joinInsert(model, field, sourceId, id)
        }
      case (PArray(_), value) => joinInsert(model, field, sourceId, value)
      case _ =>
        throw InternalException(
          s"Invalid operation PUSH_TO with value $item to field ${field.id}"
        )
    }
    insert.flatMap(_ => readOneRecord(model, sourceId, innerReadOps))
  }

  override def removeManyFrom(
      model: PModel,
      arrayField: PShapeField,
      sourcePkValue: JsValue,
      targetPkValues: Vector[JsValue],
      innerReadOps: Vector[InnerOperation]
  ): Query[JsObject] =
    for {
      _ <- targetPkValues.traverse { targetPk =>
        removeOneFrom(model, arrayField, sourcePkValue, targetPk, Vector.empty)
      }
      result <- readOneRecord(model, sourcePkValue, innerReadOps)
    } yield result

  override def removeOneFrom(
      model: PModel,
      arrayField: PShapeField,
      sourcePkValue: JsValue,
      tergetPkValue: JsValue,
      innerReadOps: Vector[InnerOperation]
  ): Query[JsObject] = {
    val refModel = arrayField.ptype match {
      case PArray(PReference(modelId)) => st.modelsById(modelId)
      case _                           => ???
    }
    val sql =
      s"DELETE FROM ${model.id.concat("_" + arrayField.id).withQuotes} WHERE ${"source_"
        .concat(model.id)
        .withQuotes} = ? AND ${"target_".concat(refModel.id).withQuotes} = ?;"
    val prep = setJsValue(sourcePkValue, 1) *> setJsValue(tergetPkValue, 2)
    HC.updateWithGeneratedKeys(Nil)(sql, prep, 0).compile.drain *>
      readOneRecord(model, sourcePkValue, innerReadOps)
  }

  override def readManyRecords(
      model: PModel,
      where: QueryWhere,
      innerReadOps: Vector[InnerOperation]
  ): Query[JsArray] = {
    val aliasedColumns =
      selectColumnsSql(Operations.primaryFieldInnerOp(model) +: innerReadOps)

    Fragment(s"SELECT $aliasedColumns FROM ${model.id.withQuotes};", Nil, None)
      .query[JsObject]
      .to[Vector]
      .flatMap(_.traverse(populateObject(model, _, innerReadOps)))
      .map(objects => JsArray(where(objects)))
  }

  override def readOneRecord(
      model: PModel,
      primaryKeyValue: JsValue,
      innerReadOps: Vector[InnerOperation]
  ): Query[JsObject] = {
    val innerOpsWithPK = innerReadOps :+ Operations.primaryFieldInnerOp(model)
    val aliasedColumns = selectColumnsSql(innerOpsWithPK)

    val sql =
      s"""SELECT $aliasedColumns FROM ${model.id.withQuotes} WHERE ${model.primaryField.id.withQuotes} = ?;"""

    val prep = setJsValue(primaryKeyValue)

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
      .zip(innerOps.map(iop => model.fieldsById(iop.targetField.field.id)))
      .traverse {
        case (iop, PModelField(_, PReference(_), _, _, _, _)) =>
          populateId(
            iop.targetModel,
            unpopulated.fields(iop.nameOrAlias),
            iop.innerReadOps
          ).map(obj => iop.nameOrAlias -> obj)
        case (
            iop,
            arrayField @ PModelField(_, PArray(_), _, _, _, _)
            ) =>
          populateArray(
            model,
            unpopulated.fields(model.primaryField.id),
            arrayField,
            iop
          ).map(vec => iop.nameOrAlias -> JsArray(vec))
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
              case _ => iop.nameOrAlias -> JsNull
            }
        case (iop, _) =>
          Monad[Query].pure {
            iop.nameOrAlias -> unpopulated.fields(iop.nameOrAlias)
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
      arrayInnerOp: InnerOperation
  ): Query[Vector[JsValue]] = {
    val sql =
      s"SELECT ${("target_" + arrayInnerOp.targetModel.id).withQuotes} " +
        s"FROM ${baseModel.id.concat("_").concat(arrayInnerOp.targetField.field.id).withQuotes} " +
        s"WHERE ${("source_" + baseModel.id).withQuotes} = ?"

    val prep = setJsValue(baseRecordId)
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
      recordModel: PModel
  ): FieldKindValueMap = {
    val fieldKindMap = modelFieldKindMap(recordModel.id)
    fieldKindMap
      .map {
        case (kind, fields) =>
          kind -> fields
            .map(f => (f, record.fields.get(f.id).getOrElse(JsNull)))
            .toVector
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

  implicit val jsObjectRead: doobie.Read[JsObject] =
    new doobie.Read[JsObject](Nil, (resultSet, _) => {
      val rsMetadata = resultSet.getMetaData
      val columnCount = rsMetadata.getColumnCount
      val keys = (1 to columnCount).map(rsMetadata.getColumnLabel).toVector
      val mapBuilder = Map.newBuilder[String, JsValue]
      for (columnIndex <- 1 to columnCount) {
        val key = keys(columnIndex - 1)
        val value = columnValueToJson(resultSet.getObject(columnIndex))
        mapBuilder += (key -> value)
      }
      JsObject(mapBuilder.result)
    })

  /** Used to get table columns as JSON
    * CAUTION: Returns JsNull if the input value's type
    * doesn't match any case.
    */
  private def columnValueToJson(value: Any): JsValue = value match {
    case null      => JsNull
    case i: Int    => JsNumber(i)
    case d: Double => JsNumber(d)
    case s: String => JsString(s)
    case d: Date   => JsString(d.toString)
    case s: Short  => JsNumber(s.toDouble)
    case l: Long   => JsNumber(l)
    case f: Float  => JsNumber(f.toDouble)
    case _         => JsNull
  }

  /** Utility function to get a `PreparedStatementIO`
    * from a JSON value.
    */
  def setJsValue(jsVal: JsValue, paramIndex: Int = 1) =
    jsVal match {
      case JsString(s)  => HPS.set(paramIndex, s)
      case JsNumber(n)  => HPS.set(paramIndex, n.toDouble)
      case JsBoolean(b) => HPS.set(paramIndex, b)
      case JsNull       => HPS.set(paramIndex, Option.empty[Int])
      case _ =>
        throw InternalException(
          s"Trying to set illegal value $jsVal at index $paramIndex in SQL query"
        )
    }

  def selectColumnsSql(innerReadOps: Vector[InnerOperation]): String =
    innerReadOps
      .filter {
        case _: InnerReadOperation => true
        case _                     => false
      }
      .map { iop =>
        val fieldId = iop.targetField.field.id.withQuotes
        val alias = iop.targetField.alias
        fieldId + alias.map(alias => s" AS ${alias.withQuotes}").getOrElse("")
      }
      .mkString(", ")

  def selectAllById(model: PModel, id: JsValue): Query[JsObject] = {
    val sql =
      s"SELECT * FROM ${model.id.withQuotes} WHERE ${model.primaryField.id} = ?;"
    HC.stream(sql, setJsValue(id), 1).head.compile.toList.map(_.head)
  }

  def joinInsert(
      model: PModel,
      field: PShapeField,
      sourceValue: JsValue,
      targetValue: JsValue
  ): Query[Unit] = {
    val joinTable = model.id.concat("_").concat(field.id).withQuotes
    val sourceField = model.id.concat("_").concat(field.id).withQuotes
    val sql =
      s"INSERT INTO $joinTable VALUES (?, ?) RETURNING $sourceField;"
    val set = setJsValue(sourceValue) *> setJsValue(targetValue, 2)
    HC.stream(sql, set, 1).compile.toVector.as(())
  }

}
