package pragma.domain

object BuiltInDefs {
  def modelDirectives(self: PModel) = Map(
    "user" -> PInterface("user", Nil, None),
    "onWrite" -> PInterface(
      "onWrite",
      PInterfaceField(
        "function",
        PFunction(Map("self" -> self, "request" -> PAny), PAny),
        None
      ) :: Nil,
      None
    ),
    "onRead" -> PInterface(
      "onRead",
      PInterfaceField(
        "function",
        PFunction(Map("request" -> PAny), PAny),
        None
      ) :: Nil,
      None
    ),
    "onDelete" -> PInterface(
      "onDelete",
      PInterfaceField(
        "function",
        PFunction(Map("request" -> PAny), PAny),
        None
      ) :: Nil,
      None
    ),
    "noStorage" -> PInterface("noStorage", Nil, None)
  )

  def fieldDirectives(
      field: PModelField,
      newFieldType: Option[PType] = None
  ) = {
    val dirs = Map(
      "uuid" -> PInterface("uuid", Nil, None),
      "autoIncrement" -> PInterface("autoIncrement", Nil, None),
      "unique" -> PInterface("unique", Nil, None),
      "primary" -> PInterface("primary", Nil, None),
      "publicCredential" -> PInterface("publicCredential", Nil, None),
      "secretCredential" -> PInterface("secretCredential", Nil, None),
      "relation" -> PInterface(
        "relation",
        List(PInterfaceField("name", PString, None)),
        None
      )
    )

    newFieldType match {
      case Some(newFieldType) =>
        dirs ++ Map(
          "typeTransformer" -> PInterface(
            "typeTransformer",
            PInterfaceField(
              "function",
              PFunction(Map(field.id -> field.ptype), newFieldType),
              None
            ) :: Nil,
            None
          ),
          "reverseTypeTransformer" -> PInterface(
            "reverseTypeTransformer",
            PInterfaceField(
              "function",
              PFunction(Map(field.id -> newFieldType), field.ptype),
              None
            ) :: Nil,
            None
          )
        )
      case None => dirs
    }
  }

  // e.g. ifSelf & ifOwner
  val builtinFunctions =
    Map.empty[String, BuiltinFunction]
}
