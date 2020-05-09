package domain

import running.pipeline.Request
import spray.json._

object BuiltInDefs {
  def modelDirectives(self: PModel) = Map(
    "user" -> PInterface("user", Nil, None),
    "onWrite" -> PInterface(
      "onWrite",
      PInterfaceField(
        "function",
        PFunction(Map("self" -> self, "request" -> Request.pType), PAny),
        None
      ) :: Nil,
      None
    ),
    "onRead" -> PInterface(
      "onRead",
      PInterfaceField(
        "function",
        PFunction(Map("request" -> Request.pType), PAny),
        None
      ) :: Nil,
      None
    ),
    "onDelete" -> PInterface(
      "onDelete",
      PInterfaceField(
        "function",
        PFunction(Map("request" -> Request.pType), PAny),
        None
      ) :: Nil,
      None
    ),
    "noStorage" -> PInterface("noStorage", Nil, None)
  )

  def fieldDirectives(model: PModel, field: PModelField) = Map(
    "uuid" -> PInterface("uuid", Nil, None),
    "autoIncrement" -> PInterface("autoIncrement", Nil, None),
    "unique" -> PInterface("unique", Nil, None),
    "primary" -> PInterface("primary", Nil, None),
    "id" -> PInterface("id", Nil, None), // auto-increment/UUID & unique
    "publicCredential" -> PInterface("publicCredential", Nil, None),
    "secretCredential" -> PInterface("secretCredential", Nil, None),
    "relation" -> PInterface(
      "relation",
      List(PInterfaceField("name", PString, None)),
      None
    )
  )

  // e.g. ifSelf & ifOwner
  val builtinFunctions =
    Map.empty[String, BuiltinFunction[JsValue, JsValue]]
}
