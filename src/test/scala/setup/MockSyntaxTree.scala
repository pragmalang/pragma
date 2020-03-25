package setup

import domain._
import domain.primitives._
import scala.collection.immutable.ListMap

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
            PInterfaceValue(ListMap(), PInterface("", List(), None)),
            FieldDirective,
            None
          ),
          Directive(
            "primary",
            PInterfaceValue(ListMap(), PInterface("", List(), None)),
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
            PInterfaceValue(ListMap(), PInterface("", List(), None)),
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
        PInterfaceValue(ListMap(), PInterface("", List(), None)),
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
              PInterfaceValue(ListMap(), PInterface("", List(), None)),
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
  val permissions = None

  val syntaxTree =
    SyntaxTree(
      Nil,
      List(businessModel, brancPModel),
      List(businessTypeEnum),
      permissions,
      None
    )
}
