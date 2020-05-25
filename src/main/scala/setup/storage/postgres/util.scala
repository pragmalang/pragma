package setup.storage.postgres

import domain._
import Constraint.ColumnConstraint.NotNull

import org.jooq.DataType
import org.jooq.util.postgres.PostgresDataType

package object utils {
  type NotNull = NotNull.type

  def toPostgresType(
      t: PType,
      isOptional: Boolean = false
  ): Option[(DataType[_], Option[NotNull])] = {
    val notNull = if (isOptional) None else Some(NotNull)
    val isNotNull = notNull.isDefined
    t match {
      case PString =>
        Some(PostgresDataType.TEXT.nullable(!isNotNull) -> notNull)
      case PInt => Some(PostgresDataType.INT8.nullable(!isNotNull) -> notNull)
      case PFloat =>
        Some(PostgresDataType.FLOAT8.nullable(!isNotNull) -> notNull)
      case PBool => Some(PostgresDataType.BOOL.nullable(!isNotNull) -> notNull)
      case PDate => Some(PostgresDataType.DATE.nullable(!isNotNull) -> notNull)
      case PFile(_, _) =>
        Some(PostgresDataType.TEXT.nullable(!isNotNull) -> notNull)
      case t: POption => toPostgresType(t, true)
      case _          => None
    }
  }

  implicit class StringOptionOps(option: Option[String]) {

    /**
      * Returns the wrapped string in case of `Some` or an empty string in case of `None`
      */
    def unwrapSafe: String = option.getOrElse("")
  }

  // {
  //   import cats.effect.IO
  //   import cats.implicits._
  //   import doobie._
  //   import doobie.implicits._
  //   import fs2.Stream
  //   import fs2.Stream.{eval, bracket}
  //   import java.sql.{PreparedStatement, ResultSet}
  //   import doobie.util.stream.repeatEvalChunks

  //   type Row = Map[String, Any]

  //   implicit val cs = IO.contextShift(ExecutionContexts.synchronous)

  //   // This escapes to raw JDBC for efficiency.
  //   @SuppressWarnings(
  //     Array(
  //       "org.wartremover.warts.Var",
  //       "org.wartremover.warts.While",
  //       "org.wartremover.warts.NonUnitStatements"
  //     )
  //   )
  //   def getNextChunkGeneric(chunkSize: Int): ResultSetIO[Seq[Row]] =
  //     FRS.raw { rs =>
  //       val md = rs.getMetaData
  //       val ks = (1 to md.getColumnCount).map(md.getColumnLabel).toList
  //       var n = chunkSize
  //       val b = Vector.newBuilder[Row]
  //       while (n > 0 && rs.next) {
  //         val mb = Map.newBuilder[String, Any]
  //         ks.foreach(k => mb += (k -> rs.getObject(k)))
  //         b += mb.result()
  //         n -= 1
  //       }
  //       b.result()
  //     }

  //   def liftProcessGeneric(
  //       chunkSize: Int,
  //       create: ConnectionIO[PreparedStatement],
  //       prep: PreparedStatementIO[Unit],
  //       exec: PreparedStatementIO[ResultSet]
  //   ): Stream[ConnectionIO, Row] = {

  //     def prepared(
  //         ps: PreparedStatement
  //     ): Stream[ConnectionIO, PreparedStatement] =
  //       eval[ConnectionIO, PreparedStatement] {
  //         val fs = FPS.setFetchSize(chunkSize)
  //         FC.embed(ps, fs *> prep).map(_ => ps)
  //       }

  //     println(prepared _)

  //     def unrolled(rs: ResultSet): Stream[ConnectionIO, Row] =
  //       repeatEvalChunks(FC.embed(rs, getNextChunkGeneric(chunkSize)))

  //     println(unrolled _)

  //     val preparedStatement: Stream[ConnectionIO, PreparedStatement] =
  //       bracket(create)(FC.embed(_, FPS.close))

  //     def results(ps: PreparedStatement): Stream[ConnectionIO, Row] =
  //       bracket(FC.embed(ps, exec))(unrolled, FC.embed(_, FRS.close))

  //     preparedStatement.flatMap(results)

  //   }

  //   def processGeneric(
  //       sql: String,
  //       prep: PreparedStatementIO[Unit],
  //       chunkSize: Int
  //   ): Stream[ConnectionIO, Row] =
  //     liftProcessGeneric(
  //       chunkSize,
  //       FC.prepareStatement(sql),
  //       prep,
  //       FPS.executeQuery
  //     )

  //   val xa = Transactor.fromDriverManager[IO](
  //     "org.postgresql.Driver",
  //     "jdbc:postgresql:world",
  //     "postgres",
  //     ""
  //   )

  //   def runl(args: List[String]): IO[Unit] =
  //     args match {
  //       case sql :: Nil =>
  //         processGeneric(sql, ().pure[PreparedStatementIO], 100)
  //           .transact(xa)
  //           .evalMap(m => IO(Console.println(m)))
  //           .compile
  //           .drain
  //       case _ => IO(Console.println("expected on arg, a query"))
  //     }

  //   runl("select * from country" :: Nil)
  // }
}
