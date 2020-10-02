package running

import pragma.domain._
import cats.implicits._
import cats.effect.Async
import spray.json._

object PFunctionExecutor {
  def execute[M[_]: Async](
      function: PFunctionValue,
      argList: List[JsValue]
  ): M[JsValue] = JsNull.pure[M].widen[JsValue]
}
