package running.storage

import domain.DomainImplicits._
import running.operations._
import cats.implicits._
import doobie._, doobie.implicits._
import domain.PModelField
import domain.PModel

object QueryAggSqlGen {

  private type SqlStr = String
  private type SqlSingleVariableStr[A] = (String, A)
  private type StartParamIndex = Int
  private type UsedTableId = String
  private type SqlQueryData =
    (SqlStr, PreparedStatementIO[Unit], StartParamIndex, Set[UsedTableId])

  def modelAggSql(agg: ModelAgg): SqlQueryData = {
    val (filterStr, filterSet, filterNextIndex, filterUsed) = accumulateSqlData(
      "AND",
      1,
      Set.empty,
      filterSql[ModelPredicate](_, _, modelPredicateSql),
      agg.filter
    )
    val fromStr = agg.from
      .map(from => s" OFFSET ${if (from - 1 < 0) 0 else from - 1} ")
      .getOrElse("")
    val toStr = agg.to.map(to => s" LIMIT $to").getOrElse("")

    (filterStr + fromStr + toStr, filterSet, filterNextIndex, filterUsed)
  }

  def arrayFieldAggSql(
      agg: ArrayFieldAgg,
      startParamIndex: StartParamIndex
  ): SqlQueryData = {
    val (filterStr, filterSet, filterNextIndex, filterUsed) = accumulateSqlData(
      "AND",
      startParamIndex,
      Set.empty,
      filterSql[QueryPredicate](
        _,
        _,
        fieldPredicateSql(agg.parentModel, agg.field, _, _)
      ),
      agg.filter
    )
    val fromStr = agg.from
      .map(from => s" OFFSET ${if (from - 1 < 0) 0 else from - 1} ")
      .getOrElse("")
    val toStr = agg.to.map(to => s" LIMIT $to").getOrElse("")

    (filterStr + fromStr + toStr, filterSet, filterNextIndex, filterUsed)
  }

  private def filterSql[P <: QueryPredicate](
      filter: QueryFilter[P],
      startParamIndex: StartParamIndex,
      predicateFn: (P, StartParamIndex) => SqlQueryData
  ): SqlQueryData = {
    val (predStr, predSet, predNextIndex, predUsed) =
      predicateFn(filter.predicate, startParamIndex)
    val (andStr, andSet, andNextIndex, andUsed) = accumulateSqlData(
      "AND",
      predNextIndex,
      predUsed,
      filterSql[P](_, _, predicateFn),
      filter.and
    )
    val (orStr, orSet, orNextIndex, orUsed) = accumulateSqlData(
      "OR",
      andNextIndex,
      andUsed,
      filterSql[P](_, _, predicateFn),
      filter.or
    )

    (
      "(" +
        (if (filter.negated) "NOT" + predStr else predStr) +
        (if (andStr.isEmpty) "" else s" AND ($andStr)") +
        (if (orStr.isEmpty) "" else s" OR ($orStr)") +
        ")",
      predSet *> andSet *> orSet,
      orNextIndex,
      orUsed
    )
  }

  private def accumulateSqlData[A](
      sqlLogicalOp: String,
      startParamIndex: StartParamIndex,
      usedTables: Set[UsedTableId],
      sqlFn: (A, StartParamIndex) => SqlQueryData,
      inputs: Seq[A]
  ): SqlQueryData =
    if (inputs.isEmpty) ("", HPS.set(()), startParamIndex, usedTables)
    else
      inputs.tail.foldLeft(sqlFn(inputs.head, startParamIndex)) {
        case ((sql, set, i, used), input) => {
          val (sql2, set2, i2, used2) = sqlFn(input, i)
          (s"($sql) $sqlLogicalOp ($sql2)", set *> set2, i2, used ++ used2)
        }
      }

  private def modelPredicateSql(
      modelPred: ModelPredicate,
      startParamIndex: StartParamIndex
  ): SqlQueryData =
    accumulateSqlData(
      "AND",
      startParamIndex,
      Set.empty,
      (pair: (String, QueryPredicate), index) =>
        fieldPredicateSql(
          modelPred.model,
          modelPred.model.fieldsById(pair._1),
          pair._2,
          index
        ),
      modelPred.fieldPredicates
    )

  private def fieldPredicateSql(
      targetModel: PModel,
      targetField: PModelField,
      pred: QueryPredicate,
      startParamIndex: StartParamIndex
  ): SqlQueryData = pred match {
    case NullPredicate(isNull) =>
      (
        s"${targetModel.id.withQuotes + "." + targetField.id.withQuotes} IS${if (isNull) ""
        else " NOT"} NULL",
        HPS.set(()),
        startParamIndex,
        Set.empty
      )
    case BoolPredicate(is) =>
      (
        s"${targetModel.id.withQuotes + "." + targetField.id.withQuotes} = ?",
        HPS.set(startParamIndex, is),
        startParamIndex + 1,
        Set.empty
      )
    case IntPredicate(lt, gt, eq, gte, lte) =>
      andAllSql(startParamIndex) {
        val columnId = targetModel.id.withQuotes + "." + targetField.id.withQuotes
        List(
          lt.map(i => s"$columnId < ?" -> i),
          gt.map(i => s"$columnId > ?" -> i),
          eq.map(i => s"$columnId = ?" -> i),
          gte.map(i => s"$columnId >= ?" -> i),
          lte.map(i => s"$columnId <= ?" -> i)
        )
      }
    case FloatPredicate(lt, gt, eq, gte, lte) =>
      andAllSql(startParamIndex) {
        val columnId = targetModel.id.withQuotes + "." + targetField.id.withQuotes
        List(
          lt.map(i => s"$columnId < ?" -> i),
          gt.map(i => s"$columnId > ?" -> i),
          eq.map(i => s"$columnId = ?" -> i),
          gte.map(i => s"$columnId >= ?" -> i),
          lte.map(i => s"$columnId <= ?" -> i)
        )
      }
    case StringPredicate(length, startsWith, endsWith, pattern) => {
      val (lengthStr, lengthSet, strStartIndex, _) =
        andAllSql(startParamIndex) {
          val columnId = targetModel.id.withQuotes + "." + targetField.id.withQuotes
          List(
            length
              .flatMap(_.lt)
              .map(lt => s"LENGTH($columnId) < ?" -> lt),
            length
              .flatMap(_.gt)
              .map(gt => s"LENGTH($columnId) > ?" -> gt),
            length
              .flatMap(_.eq)
              .map(eq => s"LENGTH($columnId) = ?" -> eq),
            length
              .flatMap(_.lte)
              .map(lte => s"LENGTH($columnId) <= ?" -> lte),
            length
              .flatMap(_.gte)
              .map(gte => s"LENGTH($columnId) >= ?" -> gte)
          )
        }
      val (strStr, strSet, nextStartIndex, _) = andAllSql(strStartIndex) {
        val columnId = targetModel.id.withQuotes + "." + targetField.id.withQuotes
        List(
          startsWith.map(sw => s"$columnId LIKE ?" -> (sw + "%")),
          endsWith.map(ew => s"$columnId LIKE ?" -> ("%" + ew)),
          pattern.map(p => s"$columnId LIKE ?" -> p)
        )
      }
      (
        s"$lengthStr AND $strStr",
        lengthSet *> strSet,
        nextStartIndex,
        Set.empty
      )
    }
    case DatePredicate(before, after, eq) =>
      andAllSql(startParamIndex) {
        val columnId = targetModel.id.withQuotes + "." + targetField.id.withQuotes
        List(
          before.map(b => s"$columnId < ?" -> b),
          after.map(a => s"$columnId > ?" -> a),
          eq.map(e => s"$columnId = ?" -> e)
        )
      }
    case modelPred @ ModelPredicate(refModel, _) => {
      val (fieldsStr, fieldsSet, fieldsNextIndex, usedTables) =
        modelPredicateSql(modelPred, startParamIndex)
      val fkEqSql =
        s" AND ${targetModel.id.withQuotes + "." + targetField.id.withQuotes} = ${refModel.id.withQuotes}.${refModel.primaryField.id.withQuotes} "
      (fieldsStr + fkEqSql, fieldsSet, fieldsNextIndex, usedTables)
    }
    case ArrayPredicate(length) => {
      val arrayTableId = (targetModel.id + "_" + targetField.id).withQuotes
      val sourceColumnId = ("source_" + targetModel.id).withQuotes
      val countStr =
        s"(SELECT COUNT(*) FROM ${arrayTableId} WHERE $arrayTableId.$sourceColumnId = ${targetModel.id.withQuotes}.${targetModel.primaryField.id.withQuotes})"
      andAllSql(startParamIndex) {
        List(
          length.lt.map(lt => s"$countStr < ?" -> lt),
          length.gt.map(gt => s"$countStr > ?" -> gt),
          length.eq.map(eq => s"$countStr = ?" -> eq),
          length.lte.map(lte => s"$countStr <= ?" -> lte),
          length.gte.map(gte => s"$countStr >= ?" -> gte)
        )
      }
    }
  }

  private def andAllSql[A: Put](
      startParamIndex: StartParamIndex
  )(
      sqlStrs: Seq[Option[SqlSingleVariableStr[A]]]
  ): SqlQueryData =
    accumulateSqlData(
      "AND",
      startParamIndex,
      Set.empty,
      (pair: (String, A), i) => (pair._1, HPS.set(i, pair._2), i + 1, Set.empty),
      sqlStrs.collect { case Some(pair) => pair }
    )

}
