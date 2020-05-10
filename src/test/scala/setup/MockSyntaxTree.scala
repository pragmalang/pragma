package setup

import domain._

object MockSyntaxTree {

  val schema =
    """
  @user
  model Business {
    username: String? 
    email: String @publicCredential @primary
    password: String @secretCredential
    branches: [Branch] 
    mainBranch: Branch? 
    businessType: BusinessType 
  }

  model Branch {
    address: String @primary
    business: Business 
  }

  enum BusinessType {
    FOOD
    CLOTHING
    OTHER
  }"""

  val businessModel = PModel(
    "Business",
    List(
      PModelField(
        "username",
        POption(PString),
        None,
        List(),
        None
      ),
      PModelField(
        "email",
        PString,
        None,
        List(
          Directive(
            "publicCredential",
            PInterfaceValue(Map(), PInterface("", List(), None)),
            FieldDirective,
            None
          ),
          Directive(
            "primary",
            PInterfaceValue(Map(), PInterface("", List(), None)),
            FieldDirective,
            None
          )
        ),
        None
      ),
      PModelField(
        "password",
        PString,
        None,
        List(
          Directive(
            "secretCredential",
            PInterfaceValue(Map(), PInterface("", List(), None)),
            FieldDirective,
            None
          )
        ),
        None
      ),
      PModelField(
        "branches",
        PArray(PReference("Branch")),
        None,
        Nil,
        None
      ),
      PModelField(
        "mainBranch",
        POption(PReference("Branch")),
        None,
        Nil,
        None
      ),
      PModelField(
        "businessType",
        PReference("BusinessType"),
        None,
        Nil,
        None
      )
    ),
    List(
      Directive(
        "user",
        PInterfaceValue(Map(), PInterface("", List(), None)),
        ModelDirective,
        None
      )
    ),
    None
  )

  val brancPModel =
    PModel(
      "Branch",
      List(
        PModelField(
          "address",
          PString,
          None,
          List(
            Directive(
              "primary",
              PInterfaceValue(Map(), PInterface("", List(), None)),
              FieldDirective,
              None
            )
          ),
          None
        ),
        PModelField(
          "business",
          PReference("Business"),
          None,
          Nil,
          None
        )
      ),
      List(),
      None
    )

  val businessTypeEnum =
    PEnum("BusinessType", List("FOOD", "CLOTHING", "OTHER"), None)
  val permissions = Permissions.empty

  val syntaxTree =
    SyntaxTree(
      Seq.empty,
      Seq(businessModel, brancPModel),
      Seq(businessTypeEnum),
      permissions,
      None
    )
}
