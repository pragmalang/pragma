package domain

import org.scalatest.FlatSpec

class RelationshipSpec extends FlatSpec {
  "Relation equality comparison" should "be correct" in {
    val code = """
        @user model User {
            username: String @publicCredential
            password: String @secretCredential
            todos: [Todo] @connection(name: "edits")
            doneTodos: [Todo]
            adminOf: Todo? @connection(name: "adminOf")
            favoriteTodo: Todo?
        }


        model Todo {
            editors: [User] @connection(name: "edits")
            admin: User @connection(name: "adminOf")
        }
        """
    val st = SyntaxTree.from(code).get

    val relationships = Relationship.from(st).map(_.toString)

    val expected = Vector(
      "ManyToMany(edits): User/todos => Todo/editors)",
      "OneToMany: User/doneTodos => Todo",
      "OneToOne(adminOf): User/adminOf => Todo/admin)",
      "OneToOne: User/favoriteTodo => Todo"
    )

    assert(expected == relationships)
  }
}
