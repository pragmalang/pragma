package setup.storage.postgres

import domain._, domain.utils._
import setup.storage._
import running.pipeline._
import doobie._
import doobie.implicits._
import cats._
import cats.implicits._
import spray.json._
import domain.Implicits._

class PostgresQueryEngine[M[_]: Monad](
    transactor: Transactor[M],
    st: SyntaxTree
) extends QueryEngine[Postgres, M] {
  import PostgresQueryEngine._

  override type Query[A] = PostgresQueryEngine.Query[A]

  def run(
      operations: Map[Option[String], Vector[Operation]]
  ): M[Either[JsObject, Vector[JsObject]]] = ???

  def createManyRecords(
      model: PModel,
      records: Vector[JsObject],
      innerReadOps: Vector[InnerOperation]
  ): ConnectionIO[JsArray] = ???

  def createOneRecord(
      model: PModel,
      record: JsObject,
      innerReadOps: Vector[InnerOperation]
  ): ConnectionIO[JsObject] = ???

  def updateManyRecords(
      model: PModel,
      recordsWithIds: Vector[JsObject],
      innerReadOps: Vector[InnerOperation]
  ): ConnectionIO[JsArray] = ???

  def updateOneRecord(
      model: PModel,
      primaryKeyValue: Either[BigInt, String],
      newRecord: JsObject,
      innerReadOps: Vector[InnerOperation]
  ): ConnectionIO[JsObject] = ???

  def deleteManyRecords(
      model: PModel,
      filter: Either[QueryFilter, Vector[Either[String, BigInt]]],
      innerReadOps: Vector[InnerOperation]
  ): ConnectionIO[JsArray] = ???

  def deleteOneRecord[ID: Put](
      model: PModel,
      primaryKeyValue: ID,
      innerReadOps: Vector[InnerOperation]
  ): ConnectionIO[JsObject] = ???

  def pushManyTo(
      model: PModel,
      field: PShapeField,
      items: Vector[JsValue],
      primaryKeyValue: Either[BigInt, String],
      innerReadOps: Vector[InnerOperation]
  ): ConnectionIO[JsArray] = ???

  def pushOneTo(
      model: PModel,
      field: PShapeField,
      item: JsValue,
      primaryKeyValue: Either[BigInt, String],
      innerReadOps: Vector[InnerOperation]
  ): ConnectionIO[JsValue] = ???

  def removeManyFrom(
      model: PModel,
      field: PShapeField,
      filter: QueryFilter,
      primaryKeyValue: Either[BigInt, String],
      innerReadOps: Vector[InnerOperation]
  ): ConnectionIO[JsArray] = ???

  def removeOneFrom(
      model: PModel,
      field: PShapeField,
      item: JsValue,
      primaryKeyValue: Either[BigInt, String],
      innerReadOps: Vector[InnerOperation]
  ): ConnectionIO[JsValue] = ???

  def readManyRecords(
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
            arrayField @ PModelField(id, PArray(PReference(_)), _, _, _, _)
            ) =>
          populateArray(
            model,
            unpopulated.fields(model.primaryField.id),
            arrayField,
            iop
          ).map(vec => iop.nameOrAlias -> JsArray(vec))
        case (
            objField,
            PModelField(id, POption(PReference(refId)), _, _, _, _)
            ) =>
          ???
        case (iop, _) => ???
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
      s"SELECT ${arrayInnerOp.operation.targetModel.id.concat("Id").withQuotes} " +
        s"FROM ${baseModel.id.concat("_").concat(arrayInnerOp.targetField.field.id).withQuotes} " +
        s"WHERE ${baseModel.id.concat("Id").withQuotes} = ?"

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
      .map { iop =>
        val fieldId = iop.targetField.field.id.withQuotes
        val alias = iop.targetField.alias
        fieldId + alias.map(alias => s" AS ${alias.withQuotes}").getOrElse("")
      }
      .mkString(", ")

}
