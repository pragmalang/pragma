package setup

import parsing.HeavenlyParser
import domain._
import domain.primitives._
import org.parboiled2.Position
import scala.collection.immutable.ListMap

object MockSyntaxTree {
  val code = """
  @user
  model Business {
    username: String @publicCredential @primary
    email: String @publicCredential
    password: String @secretCredential
    branches: [Branch]
  }

  model Branch {
    address: String @primary
    business: Business
  }
  """
  // val hConstructs = new HeavenlyParser(code).syntaxTree.run()
  val businessModel = HModel(
    "Business",
    List(
      HModelField(
        "username",
        HString,
        None,
        List(),
        Some(PositionRange(Position(32, 4, 5), Position(40, 4, 13)))
      ),
      HModelField(
        "email",
        HString,
        None,
        List(
          FieldDirective(
            "publicCredential",
            HInterfaceValue(ListMap(), HInterface("", List(), None)),
            Some(PositionRange(Position(49, 4, 22), Position(67, 4, 40)))
          ),
          FieldDirective(
            "primary",
            HInterfaceValue(ListMap(), HInterface("", List(), None)),
            Some(PositionRange(Position(67, 4, 40), Position(80, 5, 5)))
          )
        ),
        Some(PositionRange(Position(80, 5, 5), Position(85, 5, 10)))
      ),
      HModelField(
        "password",
        HString,
        None,
        List(
          FieldDirective(
            "publicCredential",
            HInterfaceValue(ListMap(), HInterface("", List(), None)),
            Some(PositionRange(Position(94, 5, 19), Position(116, 6, 5)))
          )
        ),
        Some(PositionRange(Position(116, 6, 5), Position(124, 6, 13)))
      ),
      HModelField(
        "branches",
        HArray(HReference("Branch")),
        None,
        List(
          FieldDirective(
            "secretCredential",
            HInterfaceValue(ListMap(), HInterface("", List(), None)),
            Some(PositionRange(Position(133, 6, 22), Position(155, 7, 5)))
          )
        ),
        Some(PositionRange(Position(155, 7, 5), Position(163, 7, 13)))
      )
    ),
    List(
      ModelDirective(
        "user",
        HInterfaceValue(ListMap(), HInterface("", List(), None)),
        Some(PositionRange(Position(3, 2, 3), Position(11, 3, 3)))
      )
    ),
    Some(PositionRange(Position(17, 3, 9), Position(25, 3, 17)))
  )

  val branchModel =
    HModel(
      "Branch",
      List(
        HModelField(
          "address",
          HString,
          None,
          List(),
          Some(PositionRange(Position(200, 11, 5), Position(207, 11, 12)))
        ),
        HModelField(
          "business",
          HReference("Business"),
          None,
          List(
            FieldDirective(
              "primary",
              HInterfaceValue(ListMap(), HInterface("", List(), None)),
              Some(PositionRange(Position(216, 11, 21), Position(229, 12, 5)))
            )
          ),
          Some(PositionRange(Position(229, 12, 5), Position(237, 12, 13)))
        )
      ),
      List(),
      Some(PositionRange(Position(187, 10, 9), Position(193, 10, 15)))
    )

  val permissions = Permissions(
    globalTenant = Tenant(
      "global",
      Nil,
      None
    ),
    tenents = Nil,
    None
  )

  val syntaxTree =
    SyntaxTree(Nil, Nil, List(businessModel, branchModel), Nil, permissions)
}
