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

  case class FieldDirectiveDef(dirInterface: PInterface, applicableTypes: Set[PType]) {
    def appliesTo(field: PModelField) =
      applicableTypes(PAny) || applicableTypes(field.ptype)
  }

  val fieldDirectives: Map[String, FieldDirectiveDef] =
    Map(
      "uuid" -> FieldDirectiveDef(PInterface("uuid", Nil, None), Set(PString)),
      "autoIncrement" -> FieldDirectiveDef(
        PInterface("autoIncrement", Nil, None),
        Set(PInt)
      ),
      "unique" -> FieldDirectiveDef(PInterface("unique", Nil, None), Set(PAny)),
      "primary" -> FieldDirectiveDef(
        PInterface("primary", Nil, None),
        Set(PInt, PString)
      ),
      "publicCredential" -> FieldDirectiveDef(
        PInterface("publicCredential", Nil, None),
        Set(PString, PInt)
      ),
      "secretCredential" -> FieldDirectiveDef(
        PInterface("secretCredential", Nil, None),
        Set(PString, PInt)
      )
    )

}
