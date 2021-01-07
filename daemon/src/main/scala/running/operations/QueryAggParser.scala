package running.operations

import pragma.domain._, utils._
import spray.json._
import cats.implicits._
import java.time.Instant, java.util.Date
import scala.util.Try

class QueryAggParser(st: SyntaxTree) {

  private case class AggData(
      from: Option[Int],
      to: Option[Int],
      filterJson: Vector[JsValue],
      orderBy: Option[(Option[FieldId], String)]
  )

  def parseArrayFieldAgg(
      parentModel: PModel,
      field: PModelField,
      aggObject: JsObject
  ): Either[Exception, ArrayFieldAgg] =
    for {
      aggData <- aggStandardData(aggObject)
      AggData(from, to, filtersJson, orderData) = aggData
      filters <- filtersJson.traverse(parseArrayFieldFilter(field, _))
      order <- orderData.traverse { case (fieldId, orderStr) =>
        parseArrayFieldOrderBy(orderStr, field, fieldId)
      }
    } yield ArrayFieldAgg(parentModel, field, filters, from, to, order)

  def parseModelAgg(model: PModel, aggObject: JsObject): Either[Exception, ModelAgg] =
    for {
      aggData <- aggStandardData(aggObject)
      AggData(from, to, filtersJson, orderData) = aggData
      filters <- filtersJson.traverse(parseModelFilter(model, _))
      order <- orderData.traverse {
        case (Some(fieldId), orderStr) =>
          parseModelOrderBy(orderStr, model, fieldId)
        case _ =>
          UserError(
            "`field` property of aggregation `orderBy` object must be defined for model aggregations"
          ).asLeft
      }
    } yield ModelAgg(model, filters, from, to, order)

  private def aggStandardData(
      aggObject: JsObject
  ): Either[Exception, AggData] =
    for {
      from <- aggObject.fields.get("from") match {
        case Some(JsNumber(value)) if value.isWhole => value.toInt.some.asRight
        case None                                   => None.asRight
        case _ =>
          InternalException("Invalid value for `from` in query agg").asLeft
      }
      to <- aggObject.fields.get("to") match {
        case Some(JsNumber(value)) if value.isWhole => value.toInt.some.asRight
        case None                                   => None.asRight
        case _ =>
          InternalException("Invalid value for `to` in query agg").asLeft
      }
      filterArray <- aggObject.fields.get("filter") match {
        case Some(JsArray(filters)) => filters.asRight
        case None                   => Vector.empty.asRight
        case _ =>
          InternalException(
            "Invalid value for `filter` array on query agg object"
          ).asLeft
      }
      orderBy <- aggObject.fields.get("orderBy") match {
        case None => None.asRight
        case Some(orderObj: JsObject) => {
          val orderFieldId = orderObj.fields.get("field") match {
            case Some(JsString(fieldId)) => fieldId.some.asRight
            case Some(_) =>
              InternalException(
                "`field` property of `orderBy` object must be a string"
              ).asLeft
            case None => None.asRight
          }

          val orderStr = orderObj.fields.get("order") match {
            case Some(JsString(orderStr)) => orderStr.asRight
            case Some(_) =>
              InternalException(
                "Invalid value for field `order` property of `orderBy` object"
              ).asLeft
            case None =>
              InternalException(
                "`order` property of `orderBy` object must be specified"
              ).asLeft
          }

          for (f <- orderFieldId; o <- orderStr) yield Some(f -> o)
        }
        case Some(_) =>
          UserError(s"`orderBy` field of aggregation must be an object").asLeft
      }
    } yield AggData(from, to, filterArray, orderBy)

  private val orderedPTypes: Set[PType] = Set(PInt, PFloat, PString, PDate, PBool)

  private def parseAggOrder(order: String) = order match {
    case "ASCENDING"  => AggOrder.Ascending.asRight
    case "DESCENDING" => AggOrder.Descending.asRight
    case "SHUFFLED"   => AggOrder.Shuffled.asRight
    case other        => UserError(s"Value `$other` is not a valid order").asLeft
  }

  private def parseModelOrderBy(
      orderStr: String,
      targetModel: PModel,
      orderByField: FieldId
  ): Either[UserError, ModelOrderBy] =
    if (!targetModel.fieldsById.contains(orderByField))
      UserError(
        s"Field `$orderByField` is not a field of model `${targetModel.id}`"
      ).asLeft
    else
      parseAggOrder(orderStr).map(ModelOrderBy(targetModel.fieldsById(orderByField), _))

  private def parseArrayFieldOrderBy(
      orderStr: String,
      arrayField: PModelField,
      orderByField: Option[FieldId]
  ): Either[UserError, OrderBy] = orderByField match {
    case Some(fieldId) =>
      arrayField.ptype match {
        case PArray(PReference(refId)) => {
          val refModel = st.modelsById(refId)
          if (
            refModel.fieldsById.contains(fieldId) &&
            orderedPTypes(refModel.fieldsById(fieldId).ptype)
          )
            parseAggOrder(orderStr).map { order =>
              ModelOrderBy(refModel.fieldsById(fieldId), order)
            }
          else
            UserError(
              s"Invalid ordering of `${arrayField.id}` by field `${fieldId}`"
            ).asLeft
        }
        case other =>
          UserError(
            s"Invalid ordering of field of type `${displayPType(other)}` by field `$fieldId`"
          ).asLeft
      }
    case None => {
      val elemTypeIsOrdered = arrayField.ptype match {
        case PArray(t) if orderedPTypes(t) => ().asRight
        case other =>
          UserError(
            s"Field `${arrayField.id}` of element type `${displayPType(other)}` cannot be ordered"
          ).asLeft
      }

      elemTypeIsOrdered *> parseAggOrder(orderStr).map { order =>
        PrimitiveArrayFieldOrderBy(order)
      }
    }
  }

  private def parseModelFilter(
      model: PModel,
      filter: JsValue
  ): Either[InternalException, ModelFilter] =
    for {
      filterObj <- filter match {
        case o: JsObject => o.asRight
        case _ =>
          InternalException("Filter values must be objects").asLeft
      }
      pred <- filterObj.fields.get("predicate") match {
        case Some(p: JsObject) => parseModelPredicate(model, p)
        case _ =>
          InternalException(
            "Filter predicate value is required and must be an object"
          ).asLeft
      }
      ands <- filterObj.fields.get("and") match {
        case Some(JsArray(andFilters)) =>
          andFilters.traverse(parseModelFilter(model, _))
        case None => Vector.empty.asRight
        case _ =>
          InternalException("`and` value in filter object must be an array").asLeft
      }
      ors <- filterObj.fields.get("or") match {
        case Some(JsArray(orFilters)) =>
          orFilters.traverse(parseModelFilter(model, _))
        case None => Vector.empty.asRight
        case _ =>
          InternalException("`or` value in filter object must be an array").asLeft
      }
      negated <- filterObj.fields.get("negated") match {
        case Some(b: JsBoolean) => b.value.asRight
        case None               => false.asRight
        case _ =>
          InternalException("Value of `negated` in filter must be boolean").asLeft
      }
    } yield ModelFilter(pred, ands, ors, negated)

  private def parseArrayFieldFilter(
      arrayField: PModelField,
      filter: JsValue
  ): Either[InternalException, ArrayFieldFilter] =
    for {
      filterObj <- filter match {
        case o: JsObject => o.asRight
        case _ =>
          InternalException("Array field filter values must be objects").asLeft
      }
      pred <- filterObj.fields.get("predicate") match {
        case Some(p) => parseFieldPredicate(arrayField.ptype, p)
        case _ =>
          InternalException(
            "Array field filter predicate value is required and must be an object"
          ).asLeft
      }
      ands <- filterObj.fields.get("and") match {
        case Some(JsArray(andFilters)) =>
          andFilters.traverse(parseArrayFieldFilter(arrayField, _))
        case None => Vector.empty.asRight
        case _ =>
          InternalException(
            "`and` value in array field filter object must be an array"
          ).asLeft
      }
      ors <- filterObj.fields.get("or") match {
        case Some(JsArray(orFilters)) =>
          orFilters.traverse(parseArrayFieldFilter(arrayField, _))
        case None => Vector.empty.asRight
        case _ =>
          InternalException(
            "`or` value in array field filter object must be an array"
          ).asLeft
      }
      negated <- filterObj.fields.get("negated") match {
        case Some(b: JsBoolean) => b.value.asRight
        case None               => false.asRight
        case _ =>
          InternalException("Value of `negated` in filter must be boolean").asLeft
      }
    } yield ArrayFieldFilter(arrayField, pred, ands, ors, negated)

  private def parseFieldPredicate(
      fieldType: PType,
      predicate: JsValue
  ): Either[InternalException, QueryPredicate] =
    (fieldType, predicate) match {
      case (_, o: JsObject) if o.fields.isEmpty => NullPredicate(false).asRight
      case (_, JsNull)                          => NullPredicate(true).asRight
      case (PReference(refId), o: JsObject) =>
        parseModelPredicate(st.modelsById(refId), o)
      case (POption(t), _)              => parseFieldPredicate(t, predicate)
      case (model: PModel, o: JsObject) => parseModelPredicate(model, o)
      case (PString, o: JsObject)       => parseStringPredicate(o)
      case (PInt, o: JsObject)          => parseIntPredicate(o)
      case (PFloat, o: JsObject)        => parseFloatPredicate(o)
      case (PBool, o: JsObject)         => parseBoolPredicate(o)
      case (PDate, o: JsObject)         => parseDatePredicate(o)
      case (PArray(_), o: JsObject)     => parseArrayPredicate(o)
      case (other, p) =>
        InternalException(
          s"Invalid predicate type of `${displayPType(other)}` or predicate value of `$p`"
        ).asLeft
    }

  private def parseModelPredicate(
      model: PModel,
      predicateObj: JsObject
  ): Either[InternalException, ModelPredicate] =
    predicateObj.fields.toVector
      .traverse {
        case (fieldId, fieldPred: JsObject) =>
          model.fieldsById.get(fieldId) match {
            case None =>
              InternalException(
                s"`$fieldId` in query predicate is not a field of `${model.id}`"
              ).asLeft
            case Some(field) =>
              parseFieldPredicate(field.ptype, fieldPred).map(field.id -> _)
          }
        case (f, _) =>
          InternalException(s"Predicate field `$f` must be an object").asLeft
      }
      .map { fieldPredicates =>
        ModelPredicate(model, fieldPredicates)
      }

  private def parseStringPredicate(
      predicateObj: JsObject
  ): Either[InternalException, StringPredicate] =
    for {
      length <- predicateObj.fields.get("length") match {
        case Some(intP: JsObject) => parseIntPredicate(intP).map(_.some)
        case None                 => None.asRight
        case _ =>
          InternalException(
            "Invalid `Int` predicate value in `String` predicate"
          ).asLeft
      }
      startsWith <- predicateObj.fields.get("startsWith") match {
        case Some(JsString(value)) => value.some.asRight
        case None                  => None.asRight
        case _ =>
          InternalException(
            "Invalid value for `startsWith` in `String` predicate"
          ).asLeft
      }
      endsWith <- predicateObj.fields.get("startsWith") match {
        case Some(JsString(value)) => value.some.asRight
        case None                  => None.asRight
        case _ =>
          InternalException(
            "Invalid value for `endsWith` in `String` predicate"
          ).asLeft
      }
      pattern <- predicateObj.fields.get("startsWith") match {
        case Some(JsString(value)) => value.some.asRight
        case None                  => None.asRight
        case _ =>
          InternalException(
            "Invalid value for `pattern` in `String` predicate"
          ).asLeft
      }
    } yield StringPredicate(length, startsWith, endsWith, pattern)

  private def parseIntPredicate(
      predicateObj: JsObject
  ): Either[InternalException, IntPredicate] =
    for {
      lt <- predicateObj.fields.get("lt") match {
        case Some(JsNumber(value)) if value.isWhole => value.toInt.some.asRight
        case None                                   => None.asRight
        case _ =>
          InternalException("Invalid value for `lt` in `Int` predicate").asLeft
      }
      gt <- predicateObj.fields.get("gt") match {
        case Some(JsNumber(value)) if value.isWhole => value.toInt.some.asRight
        case None                                   => None.asRight
        case _ =>
          InternalException("Invalid value for `gt` in `Int` predicate").asLeft
      }
      eq <- predicateObj.fields.get("eq") match {
        case Some(JsNumber(value)) if value.isWhole => value.toInt.some.asRight
        case None                                   => None.asRight
        case _ =>
          InternalException("Invalid value for `eq` in `Int` predicate").asLeft
      }
      lte <- predicateObj.fields.get("lte") match {
        case Some(JsNumber(value)) if value.isWhole => value.toInt.some.asRight
        case None                                   => None.asRight
        case _ =>
          InternalException("Invalid value for `lte` in `Int` predicate").asLeft
      }
      gte <- predicateObj.fields.get("gte") match {
        case Some(JsNumber(value)) if value.isWhole => value.toInt.some.asRight
        case None                                   => None.asRight
        case _ =>
          InternalException("Invalid value for `gte` in `Int` predicate").asLeft
      }
    } yield IntPredicate(lt, gt, eq, gte, lte)

  private def parseFloatPredicate(
      predicateObj: JsObject
  ): Either[InternalException, FloatPredicate] =
    for {
      lt <- predicateObj.fields.get("lt") match {
        case Some(JsNumber(value)) => value.toDouble.some.asRight
        case None                  => None.asRight
        case _ =>
          InternalException("Invalid value for `lt` in `Int` predicate").asLeft
      }
      gt <- predicateObj.fields.get("gt") match {
        case Some(JsNumber(value)) => value.toDouble.some.asRight
        case None                  => None.asRight
        case _ =>
          InternalException("Invalid value for `gt` in `Int` predicate").asLeft
      }
      eq <- predicateObj.fields.get("eq") match {
        case Some(JsNumber(value)) => value.toDouble.some.asRight
        case None                  => None.asRight
        case _ =>
          InternalException("Invalid value for `eq` in `Int` predicate").asLeft
      }
      lte <- predicateObj.fields.get("lte") match {
        case Some(JsNumber(value)) => value.toDouble.some.asRight
        case None                  => None.asRight
        case _ =>
          InternalException("Invalid value for `lte` in `Int` predicate").asLeft
      }
      gte <- predicateObj.fields.get("gte") match {
        case Some(JsNumber(value)) => value.toDouble.some.asRight
        case None                  => None.asRight
        case _ =>
          InternalException("Invalid value for `gte` in `Int` predicate").asLeft
      }
    } yield FloatPredicate(lt, gt, eq, gte, lte)

  private def parseBoolPredicate(
      predicateObj: JsObject
  ): Either[InternalException, BoolPredicate] =
    predicateObj.fields.get("eq") match {
      case Some(JsBoolean(value)) => BoolPredicate(value).asRight
      case _ =>
        InternalException("Invalid value for `eq` in `Boolean` predicate").asLeft
    }

  private def parseDatePredicate(
      predicateObj: JsObject
  ): Either[InternalException, DatePredicate] =
    for {
      before <- predicateObj.fields.get("before") match {
        case Some(JsString(value)) =>
          Try(Instant.parse(value)).toEither
            .leftMap { err =>
              InternalException("Invalid date value." + err.getMessage)
            }
            .map(Date.from(_).some)
        case None => None.asRight
        case _ =>
          InternalException("Invalid value for `before` in `Date` predicate").asLeft
      }
      after <- predicateObj.fields.get("after") match {
        case Some(JsString(value)) =>
          Try(Instant.parse(value)).toEither
            .leftMap { err =>
              InternalException("Invalid date value." + err.getMessage)
            }
            .map(Date.from(_).some)
        case None => None.asRight
        case _ =>
          InternalException("Invalid value for `after` in `Date` predicate").asLeft
      }
      eq <- predicateObj.fields.get("eq") match {
        case Some(JsString(value)) =>
          Try(Instant.parse(value)).toEither
            .leftMap { err =>
              InternalException("Invalid date value." + err.getMessage)
            }
            .map(Date.from(_).some)
        case None => None.asRight
        case _ =>
          InternalException("Invalid value for `eq` in `Date` predicate").asLeft
      }
    } yield DatePredicate(before, after, eq)

  private def parseArrayPredicate(
      predicateObj: JsObject
  ): Either[InternalException, ArrayPredicate] =
    predicateObj.fields.get("length") match {
      case Some(o: JsObject) => parseIntPredicate(o).map(ArrayPredicate(_))
      case _ =>
        InternalException("Invalid value for `length` in array predicate").asLeft
    }

}
