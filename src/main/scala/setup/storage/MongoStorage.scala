package setup.storage

import setup._

import domain.SyntaxTree
import spray.json._
import scala.util._
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
import sangria.ast._
import org.mongodb.scala.bson._
import running.pipeline.InnerOperation
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits._
import akka.stream.scaladsl.Sink
import akka.actor.ActorSystem
import domain.utils.UserError
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters._
// import com.mongodb.client.model.Sorts
// import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.Updates
import running.pipeline._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits._
import sangria.ast.{Document => GqlDocument}

case class MongoStorage(
    syntaxTree: SyntaxTree,
    url: ConnectionString,
    db: MongoDatabase
) extends NoSqlStorage {

  import MongoStorage._

  implicit val actorSystem = ActorSystem("mongo-actor-system")

  def run(
      query: GqlDocument,
      operations: Map[Option[String], Vector[Operation]]
  ): Future[Try[Either[JsObject, Vector[JsObject]]]] = ???

  override def migrate(
      migrationSteps: Vector[MigrationStep]
  ): Future[Vector[Try[Unit]]] =
    Future.sequence(migrationSteps.map[Future[Try[Unit]]] {
      case CreateModel(model)          => createCollection(model)
      case RenameModel(modelId, newId) => renameCollection(modelId, newId)
      case DeleteModel(model)          => deleteCollection(model)
      case UndeleteModel(model) =>
        undeleteCollection(model) // same as CreateModel
      case AddField(field, model) => addField(field, model)
      case ChangeFieldType(field, model, _, transformer) =>
        changeFieldType(field, model, transformer)
      case DeleteField(field, model) => deleteField(field, model)
      case UndeleteField(field, model) =>
        undeleteField(field, model) // same as AddField
      case RenameField(fieldId, newId, model) =>
        renameField(fieldId, newId, model)
    })

  def createCollection(model: PModel) =
    db.createCollection(model.id)
      .headOption()
      .map(_ => Success(()))
      .recover(Failure(_))

  def renameCollection(modelId: String, newId: String) =
    db.getCollection(modelId)
      .renameCollection(new MongoNamespace(newId))
      .headOption()
      .map(_ => Success(()))
      .recover(Failure(_))

  def deleteCollection(model: PModel) =
    db.getCollection(model.id)
      .drop()
      .headOption()
      .map(_ => Success(()))
      .recover(Failure(_))

  def undeleteCollection(model: PModel) =
    createCollection(model).map(_ => Success(())).recover(Failure(_))

  def addField(field: PModelField, model: PModel): Future[Try[Unit]] = {
    if (!Await.result(modelEmpty(model), Duration(10, TimeUnit.SECONDS)) && field.ptype
          .isInstanceOf[POption]) {
      MongoStorage
        .fromObservable(
          db.getCollection(model.id)
            .updateMany(
              BsonDocument(List.empty),
              BsonDocument(
                List("$set" -> BsonDocument(List(field.id -> BsonNull())))
              )
            )
        )
        .runForeach(_ => ())
        .map(_ => Success(()))
        .recover(
          _ =>
            Failure(
              UserError(
                s"Adding field `${field.id}` to model `${model.id}` has failed for some unkown reason"
              )
            )
        )
    } else {
      Future(
        Failure(
          UserError(
            s"""
            Model `${model.id}` has records in the database, new fields must be optional to ensure type safety.
            Try changing the type of `${model.id}.${field.id}` to `${domain.utils
              .displayPType(field.ptype, false)}?`
            """
          )
        )
      )
    }
  }

  def changeFieldType(
      field: PModelField,
      model: PModel,
      transformer: PFunctionValue[JsValue, JsValue]
  ) =
    fromObservableToFuture(
      db.getCollection(model.id)
        .find()
        .flatMap(
          doc => {
            db.getCollection(model.id)
              .findOneAndUpdate(
                BsonDocument(List.empty),
                BsonDocument(
                  List(
                    field.id -> fromJsValuetoBsonValue(
                      transformer.execute(
                        fromBsonValueToJsValue(
                          doc
                            .find {
                              case (k: String, v: BsonValue) => k == field.id
                            }
                            .get
                            ._2
                        )
                      )
                    )
                  )
                )
              )
          }
        )
    )

  def deleteField(
      field: PModelField,
      model: PModel
  ) =
    fromObservableToFuture(
      db.getCollection(model.id)
        .find()
        .flatMap(
          doc => {
            db.getCollection(model.id)
              .findOneAndUpdate(
                BsonDocument(List.empty),
                BsonDocument(
                  List(
                    field.id -> BsonUndefined()
                  )
                )
              )
          }
        )
    )

  def renameField(fieldId: String, newId: String, model: PModel) =
    fromObservableToFuture(
      db.getCollection(model.id)
        .updateMany(
          BsonDocument(List.empty),
          BsonDocument(
            List(
              "$rename" -> BsonDocument(List(fieldId -> new BsonString(newId)))
            )
          )
        )
    )

  def undeleteField(field: PModelField, model: PModel) = addField(field, model)

  override def modelEmpty(model: PModel): Future[Boolean] =
    db.getCollection(model.id).countDocuments().head().map(_ == 0)

  override def modelExists(model: PModel): Future[Boolean] =
    MongoStorage
      .fromObservable(db.listCollectionNames())
      .runFold(List.empty[String]) {
        case (acc, collectionName) => collectionName :: acc
      }
      .map(
        collectionNames => collectionNames.find(_ == model.id).isDefined
      )

  override def removeManyFrom(
      model: PModel,
      field: PShapeField,
      filter: QueryFilter,
      primaryKeyValue: Either[BigInt, String],
      innerReadOps: Vector[InnerOperation]
  ): Future[JsArray] = {
    val getDocumentByIdFilter =
      Filters.eq(model.primaryField.id, primaryKeyValue match {
        case Left(value)  => BsonInt64(value.longValue)
        case Right(value) => new BsonString(value)
      })
    fromObservable(
      db.getCollection(model.id)
        .find(getDocumentByIdFilter)
    ).mapAsync(10)(doc => {
        val filteredList = doc
          .get(field.id)
          .asInstanceOf[BsonArray]
          .asScala
          .toVector
          .filterNot(e => filter(fromBsonValueToJsValue(e)))
        val newBsonArray = new BsonArray(filteredList.asJava)
        fromObservable(
          db.getCollection(model.id)
            .updateOne(
              getDocumentByIdFilter,
              Updates.set(field.id, newBsonArray)
            )
        ).runWith(Sink.ignore)
      })
      .mapAsync(10)(
        _ =>
          fromObservable(
            db.getCollection(model.id).find(getDocumentByIdFilter).first()
          ).map(doc => doc(field.id))
            .runWith(Sink.head)
            .map(fromBsonValueToJsValue(_).asInstanceOf[JsArray])
      )
      .runWith(Sink.head)
  }
  override def removeOneFrom(
      model: PModel,
      field: PShapeField,
      item: JsValue,
      primaryKeyValue: Either[BigInt, String],
      innerReadOps: Vector[InnerOperation]
  ): Future[JsValue] = {
    val getDocumentByIdFilter =
      Filters.eq(model.primaryField.id, primaryKeyValue match {
        case Left(value)  => BsonInt64(value.longValue)
        case Right(value) => new BsonString(value)
      })
    fromObservable(
      db.getCollection(model.id)
        .find(getDocumentByIdFilter)
    ).mapAsync(10)(doc => {
        val filteredList = doc
          .get(field.id)
          .asInstanceOf[BsonArray]
          .asScala
          .toVector
          .dropFirstMatch(fromJsValuetoBsonValue(item))
        val newBsonArray = new BsonArray(filteredList.asJava)
        fromObservable(
          db.getCollection(model.id)
            .updateOne(
              getDocumentByIdFilter,
              Updates.set(field.id, newBsonArray)
            )
        ).runWith(Sink.ignore)
      })
      .map(_ => item)
      .runWith(Sink.head)
  }
  override def createManyRecords(
      model: PModel,
      records: Vector[JsObject],
      innerReadOps: Vector[InnerOperation]
  ): Future[JsArray] =
    fromObservable(
      db.getCollection(model.id)
        .insertMany(
          records.map(fromJsValuetoBsonValue(_).asInstanceOf[BsonDocument])
        )
    ).mapAsync(10)(result => {
        val ids =
          result.getInsertedIds().asScala.map(_._2.asObjectId()).toVector
        Future
          .sequence(ids.map(id => {
            fromObservable(
              db.getCollection(model.id).find(Filters.eq("_id", id))
            ).runWith(Sink.head)
              .map(
                e => fromBsonValueToJsValue(e.toBsonDocument).asJsObject
              )
          }))
          .map(v => JsArray(v))
      })
      .runWith(Sink.head)

  override def createOneRecord(
      model: PModel,
      record: JsObject,
      innerReadOps: Vector[InnerOperation]
  ): Future[JsObject] =
    createManyRecords(model, Vector(record), innerReadOps)
      .map(_.elements.head.asJsObject)
  override def deleteManyRecords(
      model: PModel,
      filter: Either[QueryFilter, Vector[Either[String, BigInt]]],
      innerReadOps: Vector[InnerOperation]
  ): Future[JsArray] = {
    val filterBson =
      Filters.in(
        model.primaryField.id,
        filter match {
          case Left(filter) => ???
          case Right(ids) =>
            ids.map {
              case Left(value)  => new BsonString(value)
              case Right(value) => BsonInt64(value.longValue)
            }
        }
      )
    fromObservable(db.getCollection(model.id).deleteMany(filterBson))
      .mapAsync(10)(
        result =>
          fromObservable(db.getCollection(model.id).find(filterBson))
            .runWith(Sink.seq)
      )
      .map(
        _.map(
          doc =>
            JsObject(
              doc
                .map(field => field._1 -> fromBsonValueToJsValue(field._2))
                .toMap
            )
        )
      )
      .map(seq => JsArray(seq.toVector))
      .runWith(Sink.head)
    ???
  }
  override def deleteOneRecord(
      model: PModel,
      primaryKeyValue: Either[BigInt, String],
      innerReadOps: Vector[InnerOperation]
  ): Future[JsObject] =
    deleteManyRecords(model, Right(Vector(primaryKeyValue.swap)), innerReadOps)
      .map(_.elements.head.asJsObject)
  override def pushManyTo(
      model: PModel,
      field: PShapeField,
      items: Vector[JsValue],
      primaryKeyValue: Either[BigInt, String],
      innerReadOps: Vector[InnerOperation]
  ): Future[JsArray] =
    fromObservable(
      db.getCollection(model.id)
        .updateOne(
          BsonDocument(List(model.primaryField.id -> (primaryKeyValue match {
            case Left(value)  => BsonInt64(value.longValue)
            case Right(value) => new BsonString(value)
          }))),
          Updates
            .pushEach(field.id, items.map(fromJsValuetoBsonValue(_)).asJava)
        )
    ).runWith(Sink.head).map(_ => JsArray(items))
  override def pushOneTo(
      model: PModel,
      field: PShapeField,
      item: JsValue,
      primaryKeyValue: Either[BigInt, String],
      innerReadOps: Vector[InnerOperation]
  ): Future[JsValue] =
    pushManyTo(model, field, Vector(item), primaryKeyValue, innerReadOps)
      .map(_.elements.head)
  override def readManyRecords(
      model: PModel,
      where: QueryWhere,
      innerReadOps: Vector[InnerOperation]
  ): Future[JsArray] = ???
  // fromObservable(db.getCollection(model.id).find())
  //   .map(
  //     doc =>
  //       where.filter
  //         .execute(
  //           JsObject(
  //             doc
  //               .map(field => field._1 -> fromBsonValueToJsValue(field._2))
  //               .toMap
  //           )
  //         )
  //         .get
  //   )
  //   .runWith(Sink.seq)
  //   .map(seq => JsArray(seq.toVector))
  override def readOneRecord(
      model: PModel,
      primaryKeyValue: Either[BigInt, String],
      innerReadOps: Vector[InnerOperation]
  ): Future[JsObject] =
    fromObservable(
      db.getCollection(model.id)
        .find(Filters.eq(model.primaryField.id, primaryKeyValue match {
          case Left(value)  => BsonInt64(value.longValue)
          case Right(value) => new BsonString(value)
        }))
    ).runWith(Sink.head)
      .map(
        doc =>
          JsObject(
            doc
              .map(field => field._1 -> fromBsonValueToJsValue(field._2))
              .toMap
          )
      )

  override def updateManyRecords(
      model: PModel,
      recordsWithIds: Vector[JsObject],
      innerReadOps: Vector[InnerOperation]
  ): Future[JsArray] = {
    val primaryKeyValues = recordsWithIds
      .map(_.fields.find(_._1 == model.primaryField.id).get._2)
      .collect {
        case JsNumber(value) => Left(value.toBigInt)
        case JsString(value) => Right(value)
      }
    val newRecords = recordsWithIds.map(
      obj => JsObject(obj.fields.filter(_._1 == model.primaryField.id))
    )
    Future
      .sequence(
        primaryKeyValues
          .zip(newRecords)
          .map(
            record => updateOneRecord(model, record._1, record._2, innerReadOps)
          )
      )
      .map(JsArray(_))
  }
  override def updateOneRecord(
      model: PModel,
      primaryKeyValue: Either[BigInt, String],
      newRecord: JsObject,
      innerReadOps: Vector[InnerOperation]
  ): Future[JsObject] = {
    val filterBson = Filters.eq(model.primaryField.id, primaryKeyValue match {
      case Left(value)  => BsonInt64(value.longValue)
      case Right(value) => new BsonString(value)
    })
    fromObservable(
      db.getCollection(model.id)
        .updateOne(
          filterBson,
          Updates.combine(
            newRecord.fields
              .map(
                field => Updates.set(field._1, fromJsValuetoBsonValue(field._2))
              )
              .toList
              .asJava
          )
        )
    ).mapAsync(10)(
        _ =>
          fromObservable(db.getCollection(model.id).find(filterBson))
            .runWith(Sink.head)
      )
      .runWith(Sink.head)
      .map(
        doc =>
          JsObject(
            doc.map(field => field._1 -> fromBsonValueToJsValue(field._2)).toMap
          )
      )
  }
}

object MongoStorage {

  implicit val actorSystem1 = ActorSystem("mongo-actor-system1")

  def fromObservableToFuture[T](observable: Observable[T]): Future[Try[Unit]] =
    fromObservable(observable)
      .runForeach(_ => ())
      .map(_ => Success(()))
      .recover(Failure(_))

  def fromObservable[T](observable: Observable[T]): Source[T, _] = {
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

  def fromBsonValueToJsValue(bson: BsonValue): JsValue = bson match {
    case arr: BsonArray =>
      JsArray(arr.asScala.toVector.map(fromBsonValueToJsValue(_)))
    case bin: BsonBinary    => fromBsonValueToJsValue(bin.asDocument())
    case bool: BsonBoolean  => JsBoolean(bool.getValue())
    case date: BsonDateTime => JsString(date.toString())
    case n: BsonDecimal128  => JsNumber(n.getValue().bigDecimalValue())
    case doc: BsonDocument =>
      JsObject(
        doc
          .filter {
            case (_, _: BsonUndefined) => false
            case _                     => true
          }
          .map { case (k, v) => (k, fromBsonValueToJsValue(v)) }
          .toMap
      )
    case n: BsonDouble                => JsNumber(n.getValue())
    case n: BsonInt32                 => JsNumber(n.getValue())
    case n: BsonInt64                 => JsNumber(n.getValue())
    case js: BsonJavaScript           => JsString(js.getCode())
    case js: BsonJavaScriptWithScope  => JsString(js.getCode())
    case _: BsonNull                  => JsNull
    case n: BsonNumber                => JsNumber(n.doubleValue())
    case id: BsonObjectId             => JsString(id.getValue().toHexString())
    case regex: BsonRegularExpression => JsString(regex.getPattern())
    case str: BsonString              => JsString(str.getValue())
    case sym: BsonSymbol              => JsString(sym.getSymbol())
    case ts: BsonTimestamp            => JsNumber(ts.getValue())
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

  implicit class SeqOps[T](seq: Seq[T]) {
    def dropFirstMatch(f: T => Boolean): Seq[T] =
      seq.find(f).map(seq.indexOf(_)).getOrElse(-1) match {
        case -1 => seq
        case 0  => seq.tail
        case index =>
          Some(seq.splitAt(index)).map(halfs => halfs._1 ++ halfs._2.tail).get
      }

    def dropFirstMatch(value: T): Seq[T] =
      seq.indexOf(value) match {
        case -1 => seq
        case 0  => seq.tail
        case index =>
          Some(seq.splitAt(index)).map(halfs => halfs._1 ++ halfs._2.tail).get
      }
  }
}
