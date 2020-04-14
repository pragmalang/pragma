package setup.storage

import domain.SyntaxTree
import setup.utils._
import spray.json._
import scala.util._
import running.pipeline.Operation
import akka.stream.scaladsl.Source
import org.mongodb.scala._
import domain.primitives._
import com.mongodb.ConnectionString
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import domain._
import org.mongodb.scala.model.Filters
import org.mongodb.scala.bson.BsonInt32
import org.bson.BsonString
import domain._
import sangria.ast._
import sangria.ast.{Document => GqlDocument}
import org.mongodb.scala.bson._
import scala.util.matching.Regex
import domain.Implicits._
import running.pipeline.InnerOperation
import running.Implicits._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits._

trait Storage {

  def run(
      query: GqlDocument,
      operations: Map[Option[String], Vector[Operation]]
  ): Future[Either[JsObject, Vector[JsObject]]] = {
    val res: Map[Option[String], Future[Map[String, JsValue]]] =
      operations
        .map {
          case (opName, ops) =>
            opName -> Future
              .sequence(ops.map { op =>
                (op.event match {
                  case Read =>
                    readOneRecord(
                      op.targetModel,
                      op.opArguments
                        .find(
                          arg =>
                            arg.name == op.targetModel.primaryField.id.small
                        )
                        .get
                        .value match {
                        case value: IntValue    => Left(BigInt(value.value))
                        case value: BigIntValue => Left(value.value)
                        case value: StringValue => Right(value.value)
                        case value =>
                          throw new InternalError(
                            s"`Storage#readOneRecord` got ${value.getClass().getName()} and it only takes `Either[BigInt, String]`"
                          )
                      },
                      op.innerReadOps
                    ).map(
                      res =>
                        op.alias
                          .getOrElse(op.event.render(op.targetModel)) -> res
                    )
                  case ReadMany =>
                    readManyRecords(
                      op.targetModel,
                      op.opArguments
                        .find(_.name == "where")
                        .get
                        .value
                        .toJson
                        .asJsObject
                        .convertTo[QueryWhere],
                      op.innerReadOps
                    ).map(
                      res =>
                        op.alias
                          .getOrElse(op.event.render(op.targetModel)) -> res
                    )
                  case Create =>
                    createOneRecord(
                      op.targetModel,
                      op.opArguments
                        .find(_.name == op.targetModel.id.small)
                        .get
                        .value
                        .toJson
                        .asJsObject,
                      op.innerReadOps
                    ).map(
                      res =>
                        op.alias
                          .getOrElse(op.event.render(op.targetModel)) -> res
                    )
                  case CreateMany =>
                    createManyRecords(
                      op.targetModel,
                      op.opArguments
                        .find(_.name == "items")
                        .get
                        .value
                        .toJson
                        .asInstanceOf[JsArray]
                        .elements
                        .map(_.asJsObject),
                      op.innerReadOps
                    ).map(
                      res =>
                        op.alias
                          .getOrElse(op.event.render(op.targetModel)) -> res
                    )
                  case Update =>
                    updateOneRecord(
                      op.targetModel,
                      op.opArguments
                        .find(
                          arg =>
                            arg.name == op.targetModel.primaryField.id.small
                        )
                        .get
                        .value match {
                        case value: IntValue    => Left(BigInt(value.value))
                        case value: BigIntValue => Left(value.value)
                        case value: StringValue => Right(value.value)
                        case value =>
                          throw new InternalError(
                            s"`Storage#readOneRecord` got ${value.getClass().getName()} and it only takes `Either[BigInt, String]`"
                          )
                      },
                      op.opArguments
                        .find(_.name == op.targetModel.id.small)
                        .get
                        .value
                        .toJson
                        .asJsObject,
                      op.innerReadOps
                    ).map(
                      res =>
                        op.alias
                          .getOrElse(op.event.render(op.targetModel)) -> res
                    )
                  case UpdateMany =>
                    updateManyRecords(
                      op.targetModel,
                      op.opArguments
                        .find(_.name == "items")
                        .get
                        .value
                        .toJson
                        .asInstanceOf[JsArray]
                        .elements
                        .map(_.asJsObject),
                      op.innerReadOps
                    ).map(
                      res =>
                        op.alias
                          .getOrElse(op.event.render(op.targetModel)) -> res
                    )
                  case Delete =>
                    deleteOneRecord(
                      op.targetModel,
                      op.opArguments
                        .find(
                          arg =>
                            arg.name == op.targetModel.primaryField.id.small
                        )
                        .get
                        .value match {
                        case value: IntValue    => Left(BigInt(value.value))
                        case value: BigIntValue => Left(value.value)
                        case value: StringValue => Right(value.value)
                        case value =>
                          throw new InternalError(
                            s"`Storage#readOneRecord` got ${value.getClass().getName()} and it only takes `Either[BigInt, String]`"
                          )
                      },
                      op.innerReadOps
                    ).map(
                      res =>
                        op.alias
                          .getOrElse(op.event.render(op.targetModel)) -> res
                    )
                  case DeleteMany => {
                    val itemsArg =
                      op.opArguments.find(_.name == "items")
                    val filter = itemsArg match {
                      case Some(arg) => {
                        Right(
                          arg.value.toJson
                            .asInstanceOf[JsArray]
                            .elements
                            .collect {
                              case item: JsString => Left(item.value)
                              case item: JsNumber =>
                                Right(item.value.toBigInt)
                            }
                        )
                      }
                      case None =>
                        Left(
                          op.directives
                            .find(_.name == "filter")
                            .get
                            .arguments
                            .find(_.name == "filter")
                            .get
                            .value
                            .toJson
                            .convertTo[QueryFilter]
                        )
                    }
                    deleteManyRecords(op.targetModel, filter, op.innerReadOps)
                      .map(
                        res =>
                          op.alias
                            .getOrElse(op.event.render(op.targetModel)) -> res
                      )
                  }
                  case PushTo(listField) =>
                    pushOneTo(
                      op.targetModel,
                      listField.get,
                      op.opArguments.find(_.name == "item").get.value.toJson,
                      op.innerReadOps
                    ).map(
                      res =>
                        op.alias
                          .getOrElse(op.event.render(op.targetModel)) -> res
                    )
                  case PushManyTo(listField) =>
                    pushManyTo(
                      op.targetModel,
                      listField.get,
                      op.opArguments
                        .find(_.name == "items")
                        .get
                        .value
                        .toJson
                        .asInstanceOf[JsArray]
                        .elements,
                      op.innerReadOps
                    ).map(
                      res =>
                        op.alias
                          .getOrElse(op.event.render(op.targetModel)) -> res
                    )
                  case RemoveFrom(listField) =>
                    removeOneFrom(
                      op.targetModel,
                      listField.get,
                      op.opArguments.find(_.name == "item").get.value.toJson,
                      op.innerReadOps
                    ).map(
                      res =>
                        op.alias
                          .getOrElse(op.event.render(op.targetModel)) -> res
                    )
                  case RemoveManyFrom(listField) =>
                    removeManyFrom(
                      op.targetModel,
                      listField.get,
                      op.opArguments
                        .find(_.name == "filter")
                        .get
                        .value
                        .toJson
                        .convertTo[QueryFilter],
                      op.innerReadOps
                    ).map(
                      res =>
                        op.alias
                          .getOrElse(op.event.render(op.targetModel)) -> res
                    )
                  case Login =>
                    throw new InternalError(
                      "`LOGIN` event can't be handled by a `Storage`"
                    )
                })
              })
              .map(_.toMap)
        }

    Future
      .sequence(res.map {
        case (gqlOpName, future) => future.map(gqlOpName -> _)
      })
      .map(_.toVector.map(_._2))
      .map(v => {
        if (v.length == 1) {
          Left(JsObject(v.head))
        } else {
          Right(v.map(JsObject(_)))
        }
      })
  }

  def migrate(): Source[JsValue, _] = Source.empty

  def bootstrap: Try[Unit]

  val dockerComposeYaml: Try[DockerCompose]

  def createManyRecords(
      model: PModel,
      records: Vector[JsObject],
      innerReadOps: Vector[InnerOperation]
  ): Future[JsArray]

  def createOneRecord(
      model: PModel,
      record: JsObject,
      innerReadOps: Vector[InnerOperation]
  ): Future[JsObject]

  def updateManyRecords(
      model: PModel,
      recordsWithIds: Vector[JsObject],
      innerReadOps: Vector[InnerOperation]
  ): Future[JsArray]

  def updateOneRecord(
      model: PModel,
      primaryKeyValue: Either[BigInt, String],
      recordWithId: JsObject,
      innerReadOps: Vector[InnerOperation]
  ): Future[JsObject]

  def deleteManyRecords(
      model: PModel,
      filter: Either[QueryFilter, Vector[Either[String, BigInt]]],
      innerReadOps: Vector[InnerOperation]
  ): Future[JsArray]

  def deleteOneRecord(
      model: PModel,
      primaryKeyValue: Either[BigInt, String],
      innerReadOps: Vector[InnerOperation]
  ): Future[JsObject]

  def pushManyTo(
      model: PModel,
      field: PModelField,
      items: Vector[JsValue],
      innerReadOps: Vector[InnerOperation]
  ): Future[JsArray]

  def pushOneTo(
      model: PModel,
      field: PModelField,
      item: JsValue,
      innerReadOps: Vector[InnerOperation]
  ): Future[JsValue]

  def removeManyFrom(
      model: PModel,
      field: PModelField,
      filter: QueryFilter,
      innerReadOps: Vector[InnerOperation]
  ): Future[JsArray]

  def removeOneFrom(
      model: PModel,
      field: PModelField,
      item: JsValue,
      innerReadOps: Vector[InnerOperation]
  ): Future[JsValue]

  def readManyRecords(
      model: PModel,
      where: QueryWhere,
      innerReadOps: Vector[InnerOperation]
  ): Future[JsArray]

  def readOneRecord(
      model: PModel,
      primaryKeyValue: Either[BigInt, String],
      innerReadOps: Vector[InnerOperation]
  ): Future[JsObject]
}

case class QueryWhere(
    filter: Option[QueryFilter],
    orderBy: Option[(String, Option[QueryOrder])],
    range: Option[Either[(BigInt, BigInt), (String, String)]],
    first: Option[Int],
    last: Option[Int],
    skip: Option[Int]
)

sealed trait QueryOrder
object ASC extends QueryOrder
object DESC extends QueryOrder

case class QueryFilter(
    not: Option[QueryFilter],
    and: Option[QueryFilter],
    or: Option[QueryFilter],
    eq: Option[(Option[String], JsValue)],
    gt: Option[(Option[String], JsValue)],
    gte: Option[(Option[String], JsValue)],
    lt: Option[(Option[String], JsValue)],
    lte: Option[(Option[String], JsValue)],
    matches: Option[(Option[String], Regex)]
)

case class MockStorage(syntaxTree: SyntaxTree) extends Storage {

  override def run(
      query: GqlDocument,
      operations: Map[Option[String], Vector[Operation]]
  ): Future[Either[JsObject, Vector[JsObject]]] =
    Future(
      Left(
        JsObject(
          Map(
            "username" -> JsString("John Doe"),
            "todos" -> JsArray(
              Vector(
                JsObject(
                  Map(
                    "content" -> JsString("Wash the dishes"),
                    "done" -> JsTrue
                  )
                ),
                JsObject(
                  Map(
                    "content" -> JsString("Pick up the kids"),
                    "done" -> JsFalse
                  )
                )
              )
            )
          )
        )
      )
    )

  def bootstrap: Try[Unit] = Success(())

  val dockerComposeYaml: Try[DockerCompose] = null
  override def removeManyFrom(
      model: PModel,
      field: PModelField,
      filter: QueryFilter,
      innerReadOps: Vector[InnerOperation]
  ): Future[JsArray] = ???

  override def removeOneFrom(
      model: PModel,
      field: PModelField,
      item: JsValue,
      innerReadOps: Vector[InnerOperation]
  ): Future[JsObject] = ???

  override def createManyRecords(
      model: PModel,
      records: Vector[JsObject],
      innerReadOps: Vector[InnerOperation]
  ): Future[JsArray] = ???

  override def createOneRecord(
      model: PModel,
      record: JsObject,
      innerReadOps: Vector[InnerOperation]
  ): Future[JsObject] = ???
  override def deleteManyRecords(
      model: PModel,
      filter: Either[QueryFilter, Vector[Either[String, BigInt]]],
      innerReadOps: Vector[InnerOperation]
  ): Future[JsArray] = ???

  override def deleteOneRecord(
      model: PModel,
      primaryKeyValue: Either[BigInt, String],
      innerReadOps: Vector[InnerOperation]
  ): Future[JsObject] = ???

  override def pushManyTo(
      model: PModel,
      field: PModelField,
      items: Vector[JsValue],
      innerReadOps: Vector[InnerOperation]
  ): Future[JsArray] = ???

  override def pushOneTo(
      model: PModel,
      field: PModelField,
      item: JsValue,
      innerReadOps: Vector[InnerOperation]
  ): Future[JsObject] = ???

  override def readManyRecords(
      model: PModel,
      where: QueryWhere,
      innerReadOps: Vector[InnerOperation]
  ): Future[JsArray] = ???

  override def readOneRecord(
      model: PModel,
      primaryKeyValue: Either[BigInt, String],
      innerReadOps: Vector[InnerOperation]
  ): Future[JsObject] = ???

  override def updateManyRecords(
      model: PModel,
      recordsWithIds: Vector[JsObject],
      innerReadOps: Vector[InnerOperation]
  ): Future[JsArray] = ???

  override def updateOneRecord(
      model: PModel,
      primaryKeyValue: Either[BigInt, String],
      recordWithId: JsObject,
      innerReadOps: Vector[InnerOperation]
  ): Future[JsObject] = ???
}

// case class MongoStorage(syntaxTree: SyntaxTree) extends Storage {

//   val url = syntaxTree.getConfigEntry("url") match {
//     case Some(url) =>
//       new ConnectionString(url.value.asInstanceOf[PStringValue].value)
//     case None => new ConnectionString("mongodb://localhost:27017")
//   }
//   val client = MongoClient(url.getConnectionString())

//   val db: Option[MongoDatabase] =
//     if (url.getDatabase() != null)
//       Some(client.getDatabase(url.getDatabase()))
//     else
//       None

//   val dbPassword =
//     if (url.getPassword() != null)
//       url.getPassword().mkString
//     else
//       "root"

//   val dbUsername =
//     if (url.getUsername() != null)
//       url.getUsername()
//     else
//       "root"

//   override def bootstrap: Try[Unit] = Success(())

//   override val dockerComposeYaml: Try[DockerCompose] = Try {
//     DockerCompose(
//       services = JsObject(
//         "mongo" -> JsObject(
//           "image" -> "mongo".toJson,
//           "restart" -> "always".toJson,
//           "environment" -> JsObject(
//             "MONGO_INITDB_ROOT_USERNAME" ->
//               dbUsername.toJson,
//             "MONGO_INITDB_ROOT_PASSWORD" ->
//               dbPassword.toJson
//           )
//         )
//       )
//     )
//   }

//   override def run(
//       query: GqlDocument,
//       operations: Vector[Operation]
//   ): Source[JsValue, _] = {
//     ???
//   }

//   override def migrate(): Source[JsValue, _] =
//     syntaxTree.models
//       .map(model => db.get.createCollection(model.id))
//       .map(observable => MongoStorage.fromObservableToSource(observable))
//       .foldLeft(Source.empty[JsValue])(
//         (acc, src) => acc.concat(src.map(_ => JsObject.empty))
//       )

//   override def createRecords(
//       model: PModel,
//       records: Vector[JsObject]
//   ): Source[JsValue, _] = ???
//   override def deleteRecords(
//       model: PModel,
//       filter: Either[QueryFilter, List[Either[String, BigInt]]]
//   ): Source[JsValue, _] = ???
//   override def updateRecords(
//       model: PModel,
//       recordsWithIds: Vector[JsObject]
//   ): Source[JsValue, _] = ???
//   override def recoverRecords(
//       model: PModel,
//       filter: Either[QueryFilter, List[Either[String, BigInt]]]
//   ): Source[JsValue, _] = ???
//   override def removeManyFrom(
//       model: PModel,
//       field: PModelField,
//       filter: QueryFilter
//   ): Source[JsValue, _] = ???
//   override def pushManyTo(
//       model: PModel,
//       field: PModelField,
//       items: Vector[JsValue]
//   ): Source[JsValue, _] = ???
//   override def readMany(model: PModel, where: QueryWhere): Source[JsValue, _] = ???
// }
object MongoStorage {
  def fromObservableToSource[T](observable: Observable[T]): Source[T, _] = {
    val publisher = new Publisher[T] {
      def subscribe(s: Subscriber[_ >: T]): Unit =
        observable.subscribe(new Observer[T] {
          def onNext(result: T) = s.onNext(result)
          def onComplete() = s.onComplete
          def onError(e: Throwable) = s.onError(e)
        })
    }
    Source.fromPublisher(publisher)
  }

  def fromJsValuetoBsonValue(value: JsValue): BsonValue = value match {
    case JsObject(fields) =>
      BsonDocument(fields.map(f => f._1 -> fromJsValuetoBsonValue(f._2)))
    case JsArray(elements) =>
      BsonArray.fromIterable(
        elements.map(el => fromJsValuetoBsonValue(el)).toIterable
      )
    case JsString(value) => new BsonString(value)
    case JsNumber(value) => BsonDecimal128(value)
    case JsTrue          => BsonBoolean(true)
    case JsFalse         => BsonBoolean(false)
    case JsNull          => BsonNull()
  }

  def fromSangriaValueToBsonValue(
      value: Value,
      ctx: Map[String, Value] = Map.empty
  ): BsonValue = value match {
    case value: IntValue => BsonInt64(value.value)
    case value: BigIntValue =>
      BsonDecimal128(BigDecimal(value.value.bigInteger))
    case value: FloatValue      => BsonDouble(value.value)
    case value: BigDecimalValue => BsonDecimal128(value.value)
    case value: StringValue     => new BsonString(value.value)
    case value: BooleanValue    => BsonBoolean(value.value)
    case value: EnumValue       => new BsonString(value.value)
    case value: ListValue =>
      BsonArray.fromIterable(value.values.map(fromSangriaValueToBsonValue(_)))
    case value: NullValue => BsonNull()
    case value: ObjectValue =>
      BsonDocument(
        value.fields.map(f => (f.name -> fromSangriaValueToBsonValue(f.value)))
      )
    case variable: VariableValue =>
      ctx
        .get(variable.name)
        .map(fromSangriaValueToBsonValue(_))
        .getOrElse(BsonNull())
  }
}
