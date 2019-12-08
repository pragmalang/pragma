package running.execution

import sangria.ast._
import sangria.execution._
import sangria.schema._
import sangria.execution.deferred.Deferred
import sangria.execution.deferred.DeferredResolver
import scala.concurrent.{Future, ExecutionContext}
import domain.SyntaxTree
import setup.storage.Storage
import java.rmi.UnexpectedException

case class GenericResolver(syntaxTree: SyntaxTree, storage: Storage)
    extends DeferredResolver[Any] {
  lazy val queryExecutor = QueryExecutor(syntaxTree, storage)
  override def resolve(
      deferred: Vector[Deferred[Any]],
      ctx: Any,
      queryState: Any
  )(implicit ec: ExecutionContext): Vector[Future[Any]] = {
    val query = deferred(0) match {
      case DeferredQuery(fieldDefinition, typeDefinition, ctx) => ctx.query
    }
    Vector(queryExecutor.execute(query).get)
  }
}

object GenericResolver {
  val fieldResolver = FieldResolver[Any](
    resolve = {
      case (typeDefinition, fieldDefinition) =>
        typeDefinition match {
          case Left(typeDefinition) =>
            ctx => DeferredQuery(fieldDefinition, typeDefinition, ctx)
          case Right(objectLikeType) =>
            ctx => DeferredQuery(fieldDefinition, objectLikeType.toAst, ctx)
        }
    }
  )
}

trait DeferredQueryShape {
  val fieldDefinition: FieldDefinition
  val typeDefinition: TypeDefinition
  val ctx: Context[Any, _]
}
case class DeferredQuery(
    fieldDefinition: FieldDefinition,
    typeDefinition: TypeDefinition,
    ctx: Context[Any, _]
) extends Deferred[DeferredQueryShape]
    with DeferredQueryShape
