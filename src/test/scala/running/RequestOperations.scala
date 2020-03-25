package running

import org.scalatest._
import sangria.macros._
import running.pipeline.Operation
import domain._, primitives._
import running.pipeline._, Operation._
import spray.json._
import scala.collection.immutable._
import org.parboiled2._

class RequestOperations extends FlatSpec {
  "Request `operations` attribute" should "be computed from user GraphQL query" in {
    val syntaxTree =
      SyntaxTree.from("""
        import "./src/test/scala/parsing/test-functions.js" as fns

        @user model User {
            username: String @publicCredential
            todos: Todo
            friend: User?
        }

        role User {
            allow READ User fns.isSelf
        }

        model Todo {
            title: String
            content: String
        }
    """).get

    val query = gql"""
        query user {
            User {
                myself: read(id: "123") {
                    _id
                    username
                    friend {
                        friend {
                            username
                            todos
                        }
                    }
                }
            }
        }
        mutation updateTodo {
            Todo {
                update(id: "22234", data: {
                    content: "Clean the dishes"
                }) {
                    content
                }
            }
        }
        """
    val request = Request(
      None,
      None,
      None,
      query,
      Left(JsObject(Map.empty[String, JsValue])),
      Map.empty,
      "http://localhost:8080/gql",
      "localhost"
    )
    val operations = Operation.operationsFrom(request)(syntaxTree)
    val expectedOps = Map(
      Some("user") -> Vector(
        Operation(
          ReadOperation,
          Map(
            "id" -> JsObject(
              TreeMap(
                "kind" -> JsString("StringValue"),
                "value" -> JsString("123")
              )
            )
          ),
          Read,
          PModel(
            "User",
            List(
              PModelField(
                "_id",
                PString,
                None,
                List(
                  Directive(
                    "primary",
                    PInterfaceValue(
                      ListMap(),
                      PInterface("primary", List(), None)
                    ),
                    FieldDirective,
                    None
                  )
                ),
                None
              ),
              PModelField(
                "username",
                PString,
                None,
                List(
                  Directive(
                    "publicCredential",
                    PInterfaceValue(ListMap(), PInterface("", List(), None)),
                    FieldDirective,
                    Some(
                      PositionRange(Position(125, 5, 30), Position(142, 5, 47))
                    )
                  )
                ),
                Some(PositionRange(Position(108, 5, 13), Position(116, 5, 21)))
              ),
              PModelField(
                "todos",
                PReference("Todo"),
                None,
                List(),
                Some(PositionRange(Position(155, 6, 13), Position(160, 6, 18)))
              ),
              PModelField(
                "friend",
                POption(PReference("User")),
                None,
                List(),
                Some(PositionRange(Position(179, 7, 13), Position(185, 7, 19)))
              )
            ),
            List(
              Directive(
                "user",
                PInterfaceValue(ListMap(), PInterface("", List(), None)),
                ModelDirective,
                Some(PositionRange(Position(77, 4, 9), Position(82, 4, 14)))
              )
            ),
            Some(PositionRange(Position(89, 4, 21), Position(93, 4, 25)))
          ),
          List(
            AliasedField(
              PModelField(
                "_id",
                PString,
                None,
                List(
                  Directive(
                    "primary",
                    PInterfaceValue(
                      ListMap(),
                      PInterface("primary", List(), None)
                    ),
                    FieldDirective,
                    None
                  )
                ),
                None
              ),
              None
            )
          ),
          None,
          None,
          List(),
          List()
        ),
        Operation(
          ReadOperation,
          Map(
            "id" -> JsObject(
              TreeMap(
                "kind" -> JsString("StringValue"),
                "value" -> JsString("123")
              )
            )
          ),
          Read,
          PModel(
            "User",
            List(
              PModelField(
                "_id",
                PString,
                None,
                List(
                  Directive(
                    "primary",
                    PInterfaceValue(
                      ListMap(),
                      PInterface("primary", List(), None)
                    ),
                    FieldDirective,
                    None
                  )
                ),
                None
              ),
              PModelField(
                "username",
                PString,
                None,
                List(
                  Directive(
                    "publicCredential",
                    PInterfaceValue(ListMap(), PInterface("", List(), None)),
                    FieldDirective,
                    Some(
                      PositionRange(Position(125, 5, 30), Position(142, 5, 47))
                    )
                  )
                ),
                Some(PositionRange(Position(108, 5, 13), Position(116, 5, 21)))
              ),
              PModelField(
                "todos",
                PReference("Todo"),
                None,
                List(),
                Some(PositionRange(Position(155, 6, 13), Position(160, 6, 18)))
              ),
              PModelField(
                "friend",
                POption(PReference("User")),
                None,
                List(),
                Some(PositionRange(Position(179, 7, 13), Position(185, 7, 19)))
              )
            ),
            List(
              Directive(
                "user",
                PInterfaceValue(ListMap(), PInterface("", List(), None)),
                ModelDirective,
                Some(PositionRange(Position(77, 4, 9), Position(82, 4, 14)))
              )
            ),
            Some(PositionRange(Position(89, 4, 21), Position(93, 4, 25)))
          ),
          List(
            AliasedField(
              PModelField(
                "username",
                PString,
                None,
                List(
                  Directive(
                    "publicCredential",
                    PInterfaceValue(ListMap(), PInterface("", List(), None)),
                    FieldDirective,
                    Some(
                      PositionRange(Position(125, 5, 30), Position(142, 5, 47))
                    )
                  )
                ),
                Some(PositionRange(Position(108, 5, 13), Position(116, 5, 21)))
              ),
              None
            )
          ),
          None,
          None,
          List(),
          List()
        ),
        Operation(
          ReadOperation,
          Map(
            "id" -> JsObject(
              TreeMap(
                "kind" -> JsString("StringValue"),
                "value" -> JsString("123")
              )
            )
          ),
          Read,
          PModel(
            "User",
            List(
              PModelField(
                "_id",
                PString,
                None,
                List(
                  Directive(
                    "primary",
                    PInterfaceValue(
                      ListMap(),
                      PInterface("primary", List(), None)
                    ),
                    FieldDirective,
                    None
                  )
                ),
                None
              ),
              PModelField(
                "username",
                PString,
                None,
                List(
                  Directive(
                    "publicCredential",
                    PInterfaceValue(ListMap(), PInterface("", List(), None)),
                    FieldDirective,
                    Some(
                      PositionRange(Position(125, 5, 30), Position(142, 5, 47))
                    )
                  )
                ),
                Some(PositionRange(Position(108, 5, 13), Position(116, 5, 21)))
              ),
              PModelField(
                "todos",
                PReference("Todo"),
                None,
                List(),
                Some(PositionRange(Position(155, 6, 13), Position(160, 6, 18)))
              ),
              PModelField(
                "friend",
                POption(PReference("User")),
                None,
                List(),
                Some(PositionRange(Position(179, 7, 13), Position(185, 7, 19)))
              )
            ),
            List(
              Directive(
                "user",
                PInterfaceValue(ListMap(), PInterface("", List(), None)),
                ModelDirective,
                Some(PositionRange(Position(77, 4, 9), Position(82, 4, 14)))
              )
            ),
            Some(PositionRange(Position(89, 4, 21), Position(93, 4, 25)))
          ),
          List(
            AliasedField(
              PModelField(
                "friend",
                POption(PReference("User")),
                None,
                List(),
                Some(PositionRange(Position(179, 7, 13), Position(185, 7, 19)))
              ),
              None
            ),
            AliasedField(
              PModelField(
                "friend",
                POption(PReference("User")),
                None,
                List(),
                Some(PositionRange(Position(179, 7, 13), Position(185, 7, 19)))
              ),
              None
            ),
            AliasedField(
              PModelField(
                "username",
                PString,
                None,
                List(
                  Directive(
                    "publicCredential",
                    PInterfaceValue(ListMap(), PInterface("", List(), None)),
                    FieldDirective,
                    Some(
                      PositionRange(Position(125, 5, 30), Position(142, 5, 47))
                    )
                  )
                ),
                Some(PositionRange(Position(108, 5, 13), Position(116, 5, 21)))
              ),
              None
            )
          ),
          None,
          None,
          List(),
          List()
        ),
        Operation(
          ReadOperation,
          Map(
            "id" -> JsObject(
              TreeMap(
                "kind" -> JsString("StringValue"),
                "value" -> JsString("123")
              )
            )
          ),
          Read,
          PModel(
            "User",
            List(
              PModelField(
                "_id",
                PString,
                None,
                List(
                  Directive(
                    "primary",
                    PInterfaceValue(
                      ListMap(),
                      PInterface("primary", List(), None)
                    ),
                    FieldDirective,
                    None
                  )
                ),
                None
              ),
              PModelField(
                "username",
                PString,
                None,
                List(
                  Directive(
                    "publicCredential",
                    PInterfaceValue(ListMap(), PInterface("", List(), None)),
                    FieldDirective,
                    Some(
                      PositionRange(Position(125, 5, 30), Position(142, 5, 47))
                    )
                  )
                ),
                Some(PositionRange(Position(108, 5, 13), Position(116, 5, 21)))
              ),
              PModelField(
                "todos",
                PReference("Todo"),
                None,
                List(),
                Some(PositionRange(Position(155, 6, 13), Position(160, 6, 18)))
              ),
              PModelField(
                "friend",
                POption(PReference("User")),
                None,
                List(),
                Some(PositionRange(Position(179, 7, 13), Position(185, 7, 19)))
              )
            ),
            List(
              Directive(
                "user",
                PInterfaceValue(ListMap(), PInterface("", List(), None)),
                ModelDirective,
                Some(PositionRange(Position(77, 4, 9), Position(82, 4, 14)))
              )
            ),
            Some(PositionRange(Position(89, 4, 21), Position(93, 4, 25)))
          ),
          List(
            AliasedField(
              PModelField(
                "friend",
                POption(PReference("User")),
                None,
                List(),
                Some(PositionRange(Position(179, 7, 13), Position(185, 7, 19)))
              ),
              None
            ),
            AliasedField(
              PModelField(
                "friend",
                POption(PReference("User")),
                None,
                List(),
                Some(PositionRange(Position(179, 7, 13), Position(185, 7, 19)))
              ),
              None
            ),
            AliasedField(
              PModelField(
                "todos",
                PReference("Todo"),
                None,
                List(),
                Some(PositionRange(Position(155, 6, 13), Position(160, 6, 18)))
              ),
              None
            )
          ),
          None,
          None,
          List(),
          List()
        )
      ),
      Some("updateTodo") -> Vector(
        Operation(
          WriteOperation,
          Map(
            "id" -> JsObject(
              TreeMap(
                "kind" -> JsString("StringValue"),
                "value" -> JsString("22234")
              )
            ),
            "data" -> JsObject(
              TreeMap(
                "fields" -> JsArray(
                  Vector(
                    JsObject(
                      TreeMap(
                        "kind" -> JsString("ObjectField"),
                        "name" -> JsString("content"),
                        "value" -> JsObject(
                          TreeMap(
                            "kind" -> JsString("StringValue"),
                            "value" -> JsString("Clean the dishes")
                          )
                        )
                      )
                    )
                  )
                ),
                "kind" -> JsString("ObjectValue")
              )
            )
          ),
          Update,
          PModel(
            "Todo",
            List(
              PModelField(
                "_id",
                PString,
                None,
                List(
                  Directive(
                    "primary",
                    PInterfaceValue(
                      ListMap(),
                      PInterface("primary", List(), None)
                    ),
                    FieldDirective,
                    None
                  )
                ),
                None
              ),
              PModelField(
                "title",
                PString,
                None,
                List(),
                Some(
                  PositionRange(Position(307, 15, 13), Position(312, 15, 18))
                )
              ),
              PModelField(
                "content",
                PString,
                None,
                List(),
                Some(
                  PositionRange(Position(333, 16, 13), Position(340, 16, 20))
                )
              )
            ),
            List(),
            Some(PositionRange(Position(288, 14, 15), Position(292, 14, 19)))
          ),
          List(
            AliasedField(
              PModelField(
                "content",
                PString,
                None,
                List(),
                Some(
                  PositionRange(Position(333, 16, 13), Position(340, 16, 20))
                )
              ),
              None
            )
          ),
          None,
          None,
          List(),
          List()
        )
      )
    )
    assert(expectedOps == operations)
  }
}
