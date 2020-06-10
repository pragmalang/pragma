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

    sql"SELECT $aliasedColumns FROM ${model.id};"
      .query[JsObject]
      .to[Vector]
      .flatMap(_.traverse(populateObject(model, _, innerReadOps)))
      .map(where(_))
      .map(it => JsArray(Vector.from(it).map(JsArray(_))))
  }

  override def readOneRecord[ID: Put](
      model: PModel,
      primaryKeyValue: ID,
      innerReadOps: Vector[InnerOperation]
  ): Query[JsObject] = {
    val aliasedColumns = selectColumnsSql(innerReadOps)

    val sql =
      s"""
      SELECT $aliasedColumns FROM ${model.id.withQuotes} WHERE ${model.primaryField.id.withQuotes} = ?;
      """

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
    val newObjFields = innerOps
      .lazyZip(innerOps.map(iop => model.fieldsById(iop.targetField.field.id)))
      .collect {
        case (iop, PModelField(id, PReference(refId), _, _, _, _)) =>
          populateId(
            iop.operation.targetModel,
            unpopulated.fields(iop.nameOrAlias),
            iop.operation.innerReadOps
          ).map(iop.nameOrAlias -> _)
        case (
            objField,
            PModelField(id, PArray(PReference(refId)), _, _, _, _)
            ) =>
          ???
        case (
            objField,
            PModelField(id, POption(PReference(refId)), _, _, _, _)
            ) =>
          ???
      }

    newObjFields.toVector.sequence.map { popFields =>
      val allFields = popFields.foldLeft(unpopulated.fields)(_ + _)
      JsObject(allFields)
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
}
object PostgresQueryEngine {
  type Query[A] = ConnectionIO[A]

  protected implicit lazy val testContextShift =
    IO.contextShift(ExecutionContexts.synchronous)

  lazy val testTransactor = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver",
    "jdbc:postgresql://test-postgres-do-user-6445746-0.a.db.ondigitalocean.com:25060/defaultdb",
    "doadmin",
    "j85b8frfhy1ja163",
    Blocker.liftExecutionContext(ExecutionContexts.synchronous)
  )

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

  def selectColumnsSql(innerReadOps: Vector[InnerOperation]): String =
    innerReadOps
      .map { iop =>
        val fieldId = iop.targetField.field.id.withQuotes
        val alias = iop.targetField.alias
        fieldId + alias.map(alias => s" AS ${alias.withQuotes}").getOrElse("")
      }
      .mkString(", ")

}
