package setup.storage.postgres

import domain._, domain.utils._
import setup.storage._
import running.pipeline._
import doobie._
import doobie.implicits._
import cats._
import cats.implicits._
import cats.effect._
import spray.json._
import domain.Implicits._
import postgres.utils._

class PostgresQueryEngine[M[_]: Monad](
    transactor: Transactor[M],
    st: SyntaxTree
)(implicit bracket: Bracket[M, Throwable])
    extends QueryEngine[Postgres[M], M] {
  import PostgresQueryEngine._

  override type Query[A] = PostgresQueryEngine.Query[A]

  override def run(
      operations: Map[Option[String], Vector[Operation]]
  ): M[JsObject] = {
    val results = operations.toVector
      .flatMap { pair =>
        pair._2.map { op =>
          op.event match {
            case domain.Read => {
              val id =
                op.opArguments
                  .find(_.name == op.targetModel.primaryField.id)
                  .map(_.value)
                  .getOrElse {
                    throw InternalException(
                      "Arguments of Read operation should contain the ID of the record to read"
                    )
                  }
              val record = readOneRecord(
                op.targetModel,
                sangriaToJson(id),
                op.innerReadOps
              )
              (pair._1.getOrElse("data") -> record.widen[JsValue]).sequence
            }
            case ReadMany => {
              // TODO: Parse QueryWhere in Operations
              val where = QueryWhere(None, None, None)
              val records =
                readManyRecords(op.targetModel, where, op.innerReadOps)
              (pair._1.getOrElse("data") -> records.widen[JsValue]).sequence
            }
            case Create => {
              val objToInsert =
                objFieldsFrom(op.opArguments)
                  .find(_._1 == op.targetModel.id.small)
                  .map(_._2.asJsObject)
                  .getOrElse(
                    throw UserError(
                      s"Create mutation takes a `${op.targetModel.id.small}` argument"
                    )
                  )
              val result = createOneRecord(
                op.targetModel,
                objToInsert,
                op.innerReadOps
              )
              (pair._1.getOrElse("data") -> result.widen[JsValue]).sequence
            }
            case CreateMany => {
              val records =
                objFieldsFrom(op.opArguments)
                  .find(_._1 == "items")
                  .map {
                    case (_, arr: JsArray) =>
                      arr.elements.map {
                        case obj: JsObject => obj
                        case nonObj =>
                          throw InternalException(
                            s"Trying to create a record with non-object value `$nonObj`"
                          )
                      }
                    case _ =>
                      throw InternalException("Value `items` must be an array")
                  }
                  .getOrElse(
                    throw InternalException(
                      "CREATE_MANY operation arguments must have an `items` field"
                    )
                  )
              val created =
                createManyRecords(op.targetModel, records, op.innerReadOps)
                  .map(JsArray(_))
              (pair._1.getOrElse("data") -> created.widen[JsValue]).sequence
            }
            case PushTo(listField) => {
              val item = op.opArguments
                .find(_.name == "item")
                .map(arg => sangriaToJson(arg.value))
                .getOrElse(
                  throw InternalException {
                    "Arguments of `PUSH_TO` operation must contain the item to be pushed"
                  }
                )
              val sourceId = op.opArguments
                .find(_.name == op.targetModel.primaryField.id)
                .map(arg => sangriaToJson(arg.value))
                .getOrElse(
                  throw InternalException {
                    "Arguments of `PUSH_TO` operation must contain the ID of the array field object"
                  }
                )
              pushOneTo(
                op.targetModel,
                listField,
                item,
                sourceId,
                op.innerReadOps
              ).widen[JsValue].map(pair._1.getOrElse("data") -> _)
            }
            case PushManyTo(listField) => {
              val items = op.opArguments
                .find(_.name == "items")
                .map(_.value)
                .map {
                  case ls: sangria.ast.ListValue => ls.values.map(sangriaToJson)
                  case _ =>
                    throw InternalException(
                      "`items` argument of `PUSH_MANY_TO` operation must be an array"
                    )
                }
                .getOrElse(
                  throw InternalException {
                    "Arguments of `PUSH_MANY_TO` operation must contain the items to be pushed"
                  }
                )
              val sourceId = op.opArguments
                .find(_.name == op.targetModel.primaryField.id)
                .map(arg => sangriaToJson(arg.value))
                .getOrElse(
                  throw InternalException {
                    "Arguments of `PUSH_TO` operation must contain the ID of the array field object"
                  }
                )
              pushManyTo(
                op.targetModel,
                listField,
                items,
                sourceId,
                op.innerReadOps
              ).widen[JsValue].map(pair._1.getOrElse("data") -> _)
            }
            case domain.Update             => ???
            case UpdateMany                => ???
            case Delete                    => ???
            case DeleteMany                => ???
            case RemoveFrom(listField)     => ???
            case RemoveManyFrom(listField) => ???
            case Login                     => ???
          }
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
  ): ConnectionIO[Vector[JsObject]] =
    records.traverse(createOneRecord(model, _, innerReadOps))

  // To be used in `INSERT` statement classification
  private val ref = "ref"
  private val prim = "prim"
  private val refArray = "refArray"
  private val primArray = "primArray"

  override def createOneRecord(
      model: PModel,
      record: JsObject,
      innerReadOps: Vector[InnerOperation]
  ): ConnectionIO[JsObject] = {
    val parallelFields = model.fields.map { mfield =>
      mfield -> record.fields.get(mfield.id).getOrElse(JsNull)
    }.toVector
    val fieldTypeMap = parallelFields
      .groupBy {
        case (PModelField(_, PReference(refId), _, _, _, _), _) =>
          if (st.modelsById.contains(refId)) ref
          else prim
        case (PModelField(_, PArray(PReference(refId)), _, _, _, _), _) =>
          if (st.modelsById.contains(refId)) refArray
          else primArray
        case (PModelField(_, POption(PReference(refId)), _, _, _, _), _) =>
          if (st.modelsById.contains(refId)) ref
          else prim
        case (PModelField(_, PArray(_), _, _, _, _), _) => primArray
        case _                                          => prim
      }
      .withDefaultValue(Vector.empty)

    val refArrayInserts = fieldTypeMap(refArray)
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

    val refInserts = fieldTypeMap(ref)
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

    val primFields = refInserts.map(fieldTypeMap(prim) ++ _)

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
        case (field, values, primaryKey) =>
          values.traverse(joinInsert(model, field, id, _))
      }
      _ <- fieldTypeMap(primArray).traverse {
        case (field, value) => joinInsert(model, field, id, value)
      }
      created <- readOneRecord(model, id, innerReadOps)
    } yield created
  }

  override def updateManyRecords(
      model: PModel,
      recordsWithIds: Vector[JsObject],
      innerReadOps: Vector[InnerOperation]
  ): ConnectionIO[JsArray] = ???

  override def updateOneRecord(
      model: PModel,
      primaryKeyValue: Either[Long, String],
      newRecord: JsObject,
      innerReadOps: Vector[InnerOperation]
  ): ConnectionIO[JsObject] = ???

  override def deleteManyRecords(
      model: PModel,
      filter: Either[QueryFilter, Vector[Either[String, Long]]],
      innerReadOps: Vector[InnerOperation]
  ): ConnectionIO[JsArray] = ???

  override def deleteOneRecord[ID: Put](
      model: PModel,
      primaryKeyValue: ID,
      innerReadOps: Vector[InnerOperation]
  ): ConnectionIO[JsObject] = ???

  override def pushManyTo(
      model: PModel,
      field: PShapeField,
      items: Vector[JsValue],
      primaryKeyValue: JsValue,
      innerReadOps: Vector[InnerOperation]
  ): ConnectionIO[JsObject] =
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
  ): ConnectionIO[JsObject] = {
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
      field: PShapeField,
      filter: QueryFilter,
      primaryKeyValue: Either[Long, String],
      innerReadOps: Vector[InnerOperation]
  ): ConnectionIO[JsArray] = ???

  override def removeOneFrom(
      model: PModel,
      field: PShapeField,
      item: JsValue,
      primaryKeyValue: Either[Long, String],
      innerReadOps: Vector[InnerOperation]
  ): ConnectionIO[JsValue] = ???

  override def readManyRecords(
      model: PModel,
      where: QueryWhere,
      innerReadOps: Vector[InnerOperation]
  ): ConnectionIO[JsArray] = {
    val aliasedColumns = selectColumnsSql(idInnerOp(model) +: innerReadOps)

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
      .map(_.head)
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
        case (iop, PModelField(id, PReference(refId), _, _, _, _)) =>
          populateId(
            iop.operation.targetModel,
            unpopulated.fields(iop.nameOrAlias),
            iop.operation.innerReadOps
          ).map(obj => iop.nameOrAlias -> obj)
        case (
            iop,
            arrayField @ PModelField(id, PArray(_), _, _, _, _)
            ) =>
          populateArray(
            model,
            unpopulated.fields(model.primaryField.id),
            arrayField,
            iop
          ).map(vec => iop.nameOrAlias -> JsArray(vec))
        case (
            iop,
            PModelField(id, POption(PReference(refId)), _, _, _, _)
            ) =>
          populateId(
            iop.operation.targetModel,
            unpopulated.fields(iop.nameOrAlias),
            iop.operation.innerReadOps
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
      s"SELECT ${("target_" + arrayInnerOp.operation.targetModel.id).withQuotes} " +
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
              arrayInnerOp.operation.innerReadOps
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
      createOneRecord(refModel, value, Vector(idInnerOp(refModel)))
        .map(_.fields(refModel.primaryField.id))

}
object PostgresQueryEngine {
  type Query[A] = ConnectionIO[A]

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
    case s: Short  => JsNumber(s)
    case l: Long   => JsNumber(l)
    case f: Float  => JsNumber(f)
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
      .filterNot(_.targetField.field.ptype.isInstanceOf[PArray])
      .map { iop =>
        val fieldId = iop.targetField.field.id.withQuotes
        val alias = iop.targetField.alias
        fieldId + alias.map(alias => s" AS ${alias.withQuotes}").getOrElse("")
      }
      .mkString(", ")

  def idInnerOp(model: PModel): InnerOperation = {
    val op = Operation(
      opKind = Operations.ReadOperation,
      gqlOpKind = sangria.ast.OperationType.Query,
      opArguments = Vector.empty,
      directives = Vector.empty,
      event = domain.Read,
      targetModel = model,
      role = None,
      user = None,
      crudHooks = Vector.empty,
      alias = None,
      innerReadOps = Vector.empty
    )
    InnerOperation(
      Operations.AliasedField(
        model.primaryField,
        None,
        Vector.empty
      ),
      op
    )
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
