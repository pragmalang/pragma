package running

import pragma.domain._
import cats.Monad
import cats.effect.Async
import spray.json._
import org.http4s.dsl._

object PFunctionValueExecutor {
  def execute[M[_]: Monad: Async](
      function: PFunctionValue,
      argList: List[JsValue]
  ): M[JsValue] = ???
}
