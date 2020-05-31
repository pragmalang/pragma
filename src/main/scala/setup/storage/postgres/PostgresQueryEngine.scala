package setup.storage.postgres

import domain._, domain.utils._
import setup.storage._
import running.pipeline._
import doobie._
import doobie.implicits._
import cats._
import cats.effect._
import spray.json._

class PostgresQueryEngine[M[_]: Monad](
    transactor: Transactor[M],
    st: SyntaxTree
) extends QueryEngine[Postgres, M] {
  import PostgresQueryEngine._

  override type Query[A] = ConnectionIO[A]

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

  def deleteOneRecord(
      model: PModel,
      primaryKeyValue: Either[BigInt, String],
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
  ): ConnectionIO[JsArray] = ???

  override def readOneRecord[ID: Put](
      model: PModel,
      primaryKeyValue: ID,
      innerReadOps: Vector[InnerOperation]
  ): ConnectionIO[JsObject] = {
    val aliasedColumns = innerReadOps
      .map { iop =>
        val fieldId = iop.targetField.field.id
        val alias = iop.targetField.alias
        fieldId + alias.map(alias => s" AS $alias").getOrElse("")
      }
      .mkString(", ")

    val sql =
      s"SELECT $aliasedColumns FROM ${model.id} WHERE ${model.primaryField.id} = ?;"
    val prep = HPS.set(primaryKeyValue)

    HC.stream(sql, prep, 2222).compile.toList.map(_.head)
  }

}
object PostgresQueryEngine {

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
        val value = jsValueFrom(resultSet.getObject(columnIndex))
        mapBuilder += (key -> value)
      }
      JsObject(mapBuilder.result)
    })

  /** Used to get table columns as JSON
    * CAUTION: Returns JsNull if the input value's type
    * doesn't match any case.
    */
  protected def jsValueFrom(value: Any): JsValue = value match {
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

}
