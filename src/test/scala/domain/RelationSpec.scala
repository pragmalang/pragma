package domain

import org.scalatest.FlatSpec

class RelationshipSpec extends FlatSpec {
  val code = """
        @user model User {
            username: String @publicCredential
            password: String @secretCredential
            todos: [Todo] @relation(name: "edits")
            doneTodos: [Todo]
            adminOf: Todo? @relation(name: "adminOf")
            favoriteTodo: Todo?
        }


        model Todo {
            editors: [User] @relation(name: "edits")
            admin: User @relation(name: "adminOf")
        }
        """

  val st = SyntaxTree.from(code).get

  "Relation equality comparison" should "be correct" in {

    val relationships = Relation.from(st).map(_.toString)

    val expected = Vector(
      "ManyToMany(edits): User/todos => Todo/editors)",
      "OneToMany: User/doneTodos => Todo",
      "OneToOne(adminOf): User/adminOf => Todo/admin)",
      "OneToOne: User/favoriteTodo => Todo"
    )

    assert(expected == relationships)
  }

  "Relation tree" should "be constructed correctly" in {
    assert(st.relations("User")("todos").isDefined)
    assert(st.relations("User")("doneTodos").isDefined)
    assert(st.relations("User")("adminOf").isDefined)
    assert(st.relations("User")("favoriteTodo").isDefined)
    assert(!st.relations("User")("username").isDefined)
    assert(st.relations("Todo").isEmpty)
  }
}
