package setup

import parsing.HeavenlyParser
import domain._
import domain.primitives._
import org.parboiled2.Position
import scala.collection.immutable.ListMap
import parsing.HeavenlyParser._

object MockSyntaxTree {
  val businessModel = HModel(
    "Business",
    List(
      HModelField(
        "username",
        HOption(HString),
        None,
        List(),
        None
      ),
      HModelField(
        "email",
        HString,
        None,
        List(
          FieldDirective(
            "publicCredential",
            HInterfaceValue(ListMap(), HInterface("", List(), None)),
            None
          ),
          FieldDirective(
            "primary",
            HInterfaceValue(ListMap(), HInterface("", List(), None)),
            None
          )
        ),
        None
      ),
      HModelField(
        "password",
        HString,
        None,
        List(
          FieldDirective(
            "secretCredential",
            HInterfaceValue(ListMap(), HInterface("", List(), None)),
            None
          )
        ),
        None
      ),
      HModelField(
        "branches",
        HArray(HReference("Branch")),
        None,
        Nil,
        None
      ),
      HModelField(
        "mainBranch",
        HOption(HReference("Branch")),
        None,
        Nil,
        None
      ),
      HModelField(
        "businessType",
        HReference("BusinessType"),
        None,
        Nil,
        None
      )
    ),
    List(
      ModelDirective(
        "user",
        HInterfaceValue(ListMap(), HInterface("", List(), None)),
        None
      ),
      ModelDirective(
        "plural",
        HInterfaceValue(
          ListMap("name" -> HStringValue("manyBusinesses")),
          HInterface("", List(), None)
        ),
        None
      )
    ),
    None
  )

  val branchModel =
    HModel(
      "Branch",
      List(
        HModelField(
          "address",
          HString,
          None,
          List(
            FieldDirective(
              "primary",
              HInterfaceValue(ListMap(), HInterface("", List(), None)),
              None
            )
          ),
          None
        ),
        HModelField(
          "business",
          HReference("Business"),
          None,
          Nil,
          None
        )
      ),
      List(),
      None
    )

  val businessTypeEnum =
    HEnum("BusinessType", List("FOOD", "CLOTHING", "OTHER"), None)
  val permissions = Permissions(
    Tenant(
      "root",
      List(
        AccessRule(
          Allow,
          ShapeResource(
            ResourceReference(
              "Book",
              None,
              Some(PositionRange(Position(33, 3, 21), Position(37, 3, 25)))
            )
          ),
          List(Create, Read, Update, Delete),
          Reference(
            "authorizors",
            Some(
              Reference(
                "f",
                None,
                Some(PositionRange(Position(50, 3, 38), Position(51, 3, 39)))
              )
            ),
            Some(PositionRange(Position(38, 3, 26), Position(51, 3, 39)))
          ),
          Some(PositionRange(Position(23, 3, 11), Position(51, 3, 39)))
        ),
        AccessRule(
          Deny,
          ShapeResource(
            ResourceReference(
              "Todo",
              None,
              Some(PositionRange(Position(84, 4, 33), Position(88, 4, 37)))
            )
          ),
          List(Create, Delete),
          Reference(
            "authorizors",
            Some(
              Reference(
                "g",
                None,
                Some(
                  PositionRange(Position(101, 4, 50), Position(102, 4, 51))
                )
              )
            ),
            Some(PositionRange(Position(89, 4, 38), Position(102, 4, 51)))
          ),
          Some(PositionRange(Position(51, 3, 39), Position(102, 4, 51)))
        )
      ),
      List(),
      None
    ),
    List(),
    Some(PositionRange(Position(23, 3, 11), Position(102, 4, 51)))
  )

  val syntaxTree =
    SyntaxTree(
      Nil,
      Nil,
      List(businessModel, branchModel),
      List(businessTypeEnum),
      permissions
    )
}
