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

class PostgresQueryEngine[M[_]: Monad](
    transactor: Transactor[M],
    st: SyntaxTree
)(implicit b: Bracket[M, Throwable])
    extends QueryEngine[Postgres[M], M] {
  import PostgresQueryEngine._

  override type Query[A] = PostgresQueryEngine.Query[A]

  override def run(
      operations: Map[Option[String], Vector[Operation]]
  ): M[JsObject] = {
    import sangria.ast._
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
                      "Argument of Read operation should contain the ID of the record to read"
                    )
                  }
              val record = id match {
                case s: StringValue =>
                  readOneRecord(op.targetModel, s.value, op.innerReadOps)
                case n: IntValue =>
                  readOneRecord(op.targetModel, n.value, op.innerReadOps)
                case _ =>
                  throw InternalException(
                    "Trying to set illegal sangria value in SQL statement"
                  )
              }
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
              import sangria.marshalling.sprayJson.SprayJsonResultMarshaller.scalarNode

              val objToInsert =
                op.opArguments
                  .map { arg =>
                    arg.name -> scalarNode(arg.value, "", Set.empty)
                  }
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
            case CreateMany                => ???
            case domain.Update             => ???
            case UpdateMany                => ???
            case Delete                    => ???
            case DeleteMany                => ???
            case PushTo(listField)         => ???
            case PushManyTo(listField)     => ???
            case RemoveFrom(listField)     => ???
            case RemoveManyFrom(listField) => ???
            case Login                     => ???
          }
        }
      }
      .sequence
      .map(fields => JsObject(fields.toMap))
    println(results)
    results.transact(transactor)
  }

  override def createManyRecords(
      model: PModel,
      records: Vector[JsObject],
      innerReadOps: Vector[InnerOperation]
  ): ConnectionIO[JsArray] =
    records
      .traverse(createOneRecord(model, _, innerReadOps))
      .map(JsArray(_))

  override def createOneRecord(
      model: PModel,
      record: JsObject,
      innerReadOps: Vector[InnerOperation]
  ): ConnectionIO[JsObject] = {
    val recordFields = record.fields.toVector.map {
      case (key, value) => (key, value) -> model.fieldsById(key)
    }
    val (nonPrimitiveFields, primitiveFields) =
      recordFields.partition {
        case (
            _,
            PModelField(_, PReference(modelRef), _, _, _, _)
            ) =>
          st.modelsById.contains(modelRef)
        case (
            _,
            PModelField(_, PArray(PReference(modelRef)), _, _, _, _)
            ) =>
          st.modelsById.contains(modelRef)
        case primitiveField => false
      }

    val nonPrimitiveInserts = nonPrimitiveFields.traverse {
      case (
          (key, record: JsObject),
          PModelField(id, PReference(modelId), _, _, _, _)
          ) =>
        createOneRecord(st.modelsById(modelId), record, Vector.empty)
          .map(_ => ())
      case (
          (key, JsArray(records)),
          PModelField(id, PArray(PReference(modelId)), _, _, _, _)
          ) =>
        createManyRecords(
          st.modelsById(modelId),
          records.map {
            case obj: JsObject => obj
            case notObj =>
              throw InternalException(
                s"Trying to insert non-object value $notObj as a record of type `$modelId`"
              )
          },
          Vector.empty
        ).map(_ => ())
      case ((_, otherJsValueType), field) =>
        throw InternalException(
          s"Trying to create a record of type `${model.id}` with an object that refers to a `${displayPType(field.ptype)}` record with ID `${otherJsValueType}` Creating records that refer to existing records is not supported yet"
        )
    }

    val primitiveColumnsSql = primitiveFields
      .map {
        case ((key, _), _) => key.withQuotes
      }
      .mkString(", ")
    val primitivePrepSts = primitiveFields.map {
      case ((_, JsString(value)), _)  => HPS.set(value)
      case ((_, JsNumber(value)), _)  => HPS.set(value)
      case ((_, JsBoolean(value)), _) => HPS.set(value)
      case _ =>
        throw InternalException(
          s"Trying to set JSON object or array as an argument for an SQL INSERT statement"
        )
    }
    println(primitivePrepSts)
    val primitivesInsertSql =
      s"INSERT INTO ${model.id.withQuotes} ($primitiveColumnsSql) VALUES (" +
        List.fill(primitivePrepSts.length)("?").mkString(", ") +
        s") RETURNING ${model.primaryField.id.withQuotes};"

    val insertedRecord: ConnectionIO[JsObject] =
      HC.stream(primitivesInsertSql, primitivePrepSts.reduce(_ *> _), 1)
        .head
        .compile
        .toList
        .map(_.head.fields(model.primaryField.id))
        .flatMap {
          case JsString(s) => readOneRecord(model, s, innerReadOps)
          case JsNumber(n) => readOneRecord(model, n.toLong, innerReadOps)
          case _ =>
            throw InternalException(
              "Trying to use invalid ID JSON in INSERT-RETURNING query"
            )
        }

    for {
      _ <- nonPrimitiveInserts
      created <- insertedRecord
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
      primaryKeyValue: Either[Long, String],
      innerReadOps: Vector[InnerOperation]
  ): ConnectionIO[JsArray] = ???

  override def pushOneTo(
      model: PModel,
      field: PShapeField,
      item: JsValue,
      primaryKeyValue: Either[Long, String],
      innerReadOps: Vector[InnerOperation]
  ): ConnectionIO[JsValue] = ???

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
    val aliasedColumns = selectColumnsSql(innerReadOps)

    Fragment(s"SELECT $aliasedColumns FROM ${model.id.withQuotes};", Nil, None)
      .query[JsObject]
      .to[Vector]
      .flatMap(_.traverse(populateObject(model, _, innerReadOps)))
      .map(objects => JsArray(where(objects)))
  }

  override def readOneRecord[ID: Put](
      model: PModel,
      primaryKeyValue: ID,
      innerReadOps: Vector[InnerOperation]
  ): Query[JsObject] = {
    val innerOpsWithPK = innerReadOps :+ Operations.primaryFieldInnerOp(model)
    val aliasedColumns = selectColumnsSql(innerOpsWithPK)

    val sql =
      s"""SELECT $aliasedColumns FROM ${model.id.withQuotes} WHERE ${model.primaryField.id.withQuotes} = ?;"""

    val prep = HPS.set(primaryKeyValue)

    HC.stream(sql, prep, 1)
      .head
      .compile
      .toList
      .map(_.head)
      .flatMap(populateObject(model, _, innerReadOps))
  }

  def populateObject(
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
      case JsString(v) => readOneRecord(model, v, iops).widen[JsValue]
      case JsNumber(v) => readOneRecord(model, v.toLong, iops).widen[JsValue]
      case _           => JsNull.pure[Query].widen[JsValue]
    }

  def populateArray(
      baseModel: PModel,
      baseRecordId: JsValue,
      arrayField: PModelField,
      arrayInnerOp: InnerOperation
  ): Query[Vector[JsValue]] = {
    val sql =
      s"SELECT ${("target_" + arrayInnerOp.operation.targetModel.id).withQuotes} " +
        s"FROM ${baseModel.id.concat("_").concat(arrayInnerOp.targetField.field.id).withQuotes} " +
        s"WHERE ${("source_" + baseModel.id).withQuotes} = ?"

    val prep = idPrepStatement(baseRecordId)
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
    * from an ID JSON value.
    * NOTE: Only `JsString`s and `JsNumbers`s are valid.
    */
  def idPrepStatement(id: JsValue) =
    id match {
      case JsString(s) => HPS.set(s)
      case JsNumber(n) => HPS.set(n.toLong)
      case _ =>
        throw InternalException(
          s"Trying to set invalid ID of value $id in SQL query (IDs must either be strings or integers)"
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

}
