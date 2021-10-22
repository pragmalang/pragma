package running.storage.postgres

import running.storage._
import running.storage.postgres._
import org.scalatest._, funsuite.AnyFunSuite
import SQLMigrationStep._
import AlterTableAction._
import pragma.domain._
import org.parboiled2.Position

import doobie._
import doobie.implicits._
import cats.effect._
import cats.implicits._
import pragma.jwtUtils.JwtCodec
import pragma.domain.utils.UserError

class PostgresMigrationEngineSpec extends AnyFunSuite {

  implicit val cs = IO.contextShift(ExecutionContexts.synchronous)

  def transactorFromDbName(dbName: String) =
    Transactor.fromDriverManager[IO](
      "org.postgresql.Driver",
      s"jdbc:postgresql://localhost:5432/${dbName}",
      "test",
      "test",
      Blocker.liftExecutionContext(ExecutionContexts.synchronous)
    )

  val transactor = transactorFromDbName("test")

  val jc = new JwtCodec("123456")

  def removeAllTablesFromDb(dbName: String): IO[Unit] =
    sql"""
      DROP SCHEMA public CASCADE;
      CREATE SCHEMA public;
      GRANT ALL ON SCHEMA public TO test;
      GRANT ALL ON SCHEMA public TO public;
    """.update.run.void.transact(transactorFromDbName(dbName))

  test("Adding new models in a migration works") {

    val prevCode = """
    @1 @user
    model User {
      @1 id: String @uuid
      @2 username: String @primary @publicCredential
      @3 password: String @secretCredential
      @4 isVerified: Boolean = false
      @5 todos: [Todo]
    }

    @2 model Todo {
      @1 title: String @primary
    }

    config { projectName = "test" }
    """
    val prevSyntaxTree = SyntaxTree.from(prevCode).get

    val code = """
    @1 @user
    model User {
      @1 id: String @uuid
      @2 username: String @primary @publicCredential
      @3 password: String @secretCredential
      @4 isVerified: Boolean = false
      @5 todos: [Todo]
    }

    @2 model Todo {
      @1 title: String @primary
    }

    @3 model Admin {
      @1 username: String @primary
    }

    config { projectName = "test" }
    """
    val newSyntaxTree = SyntaxTree.from(code).get

    val testStorage = new TestStorage(newSyntaxTree)

    val migrationEngine = testStorage.migrationEngine

    val expected = Vector(
      CreateModel(
        PModel(
          "Admin",
          List(
            PModelField(
              "username",
              PString,
              None,
              1,
              List(
                Directive(
                  "primary",
                  PInterfaceValue(Map(), PInterface("", List(), None)),
                  FieldDirective,
                  Some(
                    PositionRange(
                      Position(327, 16, 27),
                      Position(335, 16, 35)
                    )
                  )
                )
              ),
              Some(
                PositionRange(Position(310, 16, 10), Position(318, 16, 18))
              )
            )
          ),
          List(),
          3,
          Some(PositionRange(Position(293, 15, 14), Position(298, 15, 19)))
        )
      )
    )

    assert(
      migrationEngine
        .inferedMigrationSteps(newSyntaxTree, prevSyntaxTree, Map.empty)
        .getOrElse(fail()) == expected
    )
  }

  test("Deleting models in a migration works") {

    val prevCode = """
    @1 @user
    model User {
      @1 id: String @uuid
      @2 username: String @primary @publicCredential
      @3 password: String @secretCredential
      @4 isVerified: Boolean = false
      @5 todos: [Todo]
    }

    @2 model Todo {
      @1 title: String @primary
    }

    @3 model Admin {
      @1 username: String @primary
    }

    config { projectName = "test" }
    """
    val prevSyntaxTree = SyntaxTree.from(prevCode).get

    val code = """
    @1 @user
    model User {
      @1 id: String @uuid
      @2 username: String @primary @publicCredential
      @3 password: String @secretCredential
      @4 isVerified: Boolean = false
      @5 todos: [Todo]
    }

    @2 model Todo {
      @1 title: String @primary
    }

    config { projectName = "test" }
    """
    val newSyntaxTree = SyntaxTree.from(code).get

    val testStorage = new TestStorage(newSyntaxTree)

    val migrationEngine = testStorage.migrationEngine

    val expected = Vector(DropTable("Admin"))
    assert {
      migrationEngine
        .migration(prevSyntaxTree, Map.empty)
        .unsafeRunSync()
        .sqlSteps == expected
    }
  }

  test("Renaming models in a migration works") {

    val prevCode = """
    @user
    @1 model User {
      @1 id: String @uuid
      @2 username: String @primary @publicCredential
      @3 password: String @secretCredential
      @4 isVerified: Boolean = false
      @5 todos: [Todo]
    }

    @2 model Todo {
      @1 title: String @primary
    }

    @3 model Admin {
      @1 username: String @primary
    }

    config { projectName = "test" }
    """
    val prevSyntaxTree = SyntaxTree.from(prevCode).get

    val code = """
    @user
    @1 model User {
      @1 id: String @uuid
      @2 username: String @primary @publicCredential
      @3 password: String @secretCredential
      @4 isVerified: Boolean = false
      @5 todos: [Todo]
    }

    @2 model Todo {
      @1 title: String @primary
    }

    @3 model Admin1 {
      @1 username: String @primary
    }

    config { projectName = "test" }
    """
    val newSyntaxTree = SyntaxTree.from(code).get

    val testStorage = new TestStorage(newSyntaxTree)

    val migrationEngine = testStorage.migrationEngine

    val expected = Vector(RenameTable("Admin", "Admin1"))
    assert(
      migrationEngine
        .migration(prevSyntaxTree, Map.empty)
        .unsafeRunSync()
        .sqlSteps == expected
    )
  }

  test("Adding fields to models in a migration works") {

    val prevCode = """
    @user
    @1 model User {
      @1 id: String @uuid
      @2 username: String @primary @publicCredential
      @3 password: String @secretCredential
      @4 isVerified: Boolean = false
      @5 todos: [Todo]
    }

    @2 model Todo {
      @1 title: String @primary
    }

    @user
    @3 model Admin {
      @1 username: String @primary @publicCredential
    }

    config { projectName = "test" }
    """
    val prevSyntaxTree = SyntaxTree.from(prevCode).get

    val code = """
    @user
    @1 model User {
      @1 id: String @uuid
      @2 username: String @primary @publicCredential
      @3 password: String @secretCredential
      @4 isVerified: Boolean = false
      @5 todos: [Todo]
    }

    @2 model Todo {
      @1 title: String @primary
    }

    @user
    @3 model Admin {
      @1 username: String @primary @publicCredential
      @2 password: String @secretCredential
    }

    config { projectName = "test" }
    """
    val newSyntaxTree = SyntaxTree.from(code).get

    val testStorage = new TestStorage(newSyntaxTree)

    val migrationEngine = testStorage.migrationEngine

    val expected = DeferredAddField(
      prevSyntaxTree.modelsById("Admin"),
      newSyntaxTree.modelsById("Admin").fieldsById("password")
    )

    assert(
      migrationEngine
        .migration(prevSyntaxTree, Map.empty.withDefaultValue(false))
        .unsafeRunSync()
        .sqlSteps
        .head == expected
    )
  }

  test("Deleting fields from models in a migration works") {

    val prevCode = """
    @user
    @1 model User {
      @1 id: String @uuid
      @2 username: String @primary @publicCredential
      @3 password: String @secretCredential
      @4 isVerified: Boolean = false
      @5 todos: [Todo]
    }

    @2 model Todo {
      @1 title: String @primary
    }

    @user
    @3 model Admin {
      @1 username: String @primary @publicCredential
      @2 password: String @secretCredential
    }

    config { projectName = "test" }
    """
    val prevSyntaxTree = SyntaxTree.from(prevCode).get

    val code = """
    @user
    @1 model User {
      @1 id: String @uuid
      @2 username: String @primary @publicCredential
      @3 password: String @secretCredential
      @4 isVerified: Boolean = false
      @5 todos: [Todo]
    }

    @2 model Todo {
      @1 title: String @primary
    }

    @user
    @3 model Admin {
      @1 username: String @primary @publicCredential
    }

    config { projectName = "test" }
    """
    val newSyntaxTree = SyntaxTree.from(code).get

    val testStorage = new TestStorage(newSyntaxTree)

    val migrationEngine = testStorage.migrationEngine

    val expected = Vector(AlterTable("Admin", DropColumn("password", true)))
    assert(
      migrationEngine
        .migration(prevSyntaxTree, Map.empty)
        .unsafeRunSync()
        .sqlSteps == expected
    )
  }

  test("Renaming model fields in a migration works") {

    val prevCode = """
    @user
    @1 model User {
      @1 id: String @uuid
      @2 username: String @primary @publicCredential
      @3 password: String @secretCredential
      @4 isVerified: Boolean = false
      @5 todos: [Todo]
    }

    @2 model Todo {
      @1 title: String @primary
    }

    @3 model Admin {
      @1 username: String @primary @publicCredential
      @2 password: String @secretCredential
    }

    config { projectName = "test" }
    """
    val prevSyntaxTree = SyntaxTree.from(prevCode).get

    val code = """
    @user
    @1 model User {
      @1 id: String @uuid
      @2 username: String @primary @publicCredential
      @3 password: String @secretCredential
      @4 isVerified: Boolean = false
      @5 todos: [Todo]
    }

    @2 model Todo {
      @1 title: String @primary
    }

    @3 model Admin {
      @1 username: String @primary @publicCredential
      @2 passcode: String @secretCredential
    }

    config { projectName = "test" }
    """
    val newSyntaxTree = SyntaxTree.from(code).get

    val testStorage = new TestStorage(newSyntaxTree)

    val migrationEngine = testStorage.migrationEngine

    val expected =
      Vector(AlterTable("Admin", RenameColumn("password", "passcode")))
    assert(
      migrationEngine
        .migration(prevSyntaxTree, Map.empty)
        .unsafeRunSync()
        .sqlSteps == expected
    )
  }

  test("Renaming model and one of it's fields in a migration works") {

    val prevCode = """
    @user
    @1 model User {
      @1 id: String @uuid
      @2 username: String @primary @publicCredential
      @3 password: String @secretCredential
      @4 isVerified: Boolean = false
      @5 todos: [Todo]
    }

    @2 model Todo {
      @1 title: String @primary
    }

    @3 model Admin {
      @1 username: String @primary @publicCredential
      @2 password: String @secretCredential
    }

    config { projectName = "test" }
    """
    val prevSyntaxTree = SyntaxTree.from(prevCode).get

    val code = """
    @user
    @1 model User {
      @1 id: String @uuid
      @2 username: String @primary @publicCredential
      @3 password: String @secretCredential
      @4 isVerified: Boolean = false
      @5 todos: [Todo]
    }

    @2 model Todo {
      @1 title: String @primary
    }

    @3 model Admin1 {
      @1 username: String @primary @publicCredential
      @2 passcode: String @secretCredential
    }

    config { projectName = "test" }
    """
    val newSyntaxTree = SyntaxTree.from(code).get

    val testStorage = new TestStorage(newSyntaxTree)

    val migrationEngine = testStorage.migrationEngine

    val expected =
      Vector(
        AlterTable("Admin", RenameColumn("password", "passcode")),
        RenameTable("Admin", "Admin1")
      )
    assert(
      migrationEngine
        .migration(prevSyntaxTree, Map.empty)
        .unsafeRunSync()
        .sqlSteps == expected
    )
  }

  test(
    "Renaming models that are dependency to other models in a migration works"
  ) {

    val prevCode = """
    @user
    @1 model User {
      @1 id: String @uuid
      @2 username: String @primary @publicCredential
      @3 password: String @secretCredential
      @4 isVerified: Boolean = false
      @5 todos: [Todo]
    }

    @2 model Todo {
      @1 title: String @primary
    }

    @3 model Admin {
      @1 username: String @primary @publicCredential
      @2 password: String @secretCredential
    }

    config { projectName = "test" }
    """
    val prevSyntaxTree = SyntaxTree.from(prevCode).get

    val code = """
    @user
    @1 model User {
      @1 id: String @uuid
      @2 username: String @primary @publicCredential
      @3 password: String @secretCredential
      @4 isVerified: Boolean = false
      @5 todos: [Todo1]
    }

    @2 model Todo1 {
      @1 title: String @primary
    }

    @3 model Admin {
      @1 username: String @primary @publicCredential
      @2 password: String @secretCredential
    }

    config { projectName = "test" }
    """
    val newSyntaxTree = SyntaxTree.from(code).get

    val testStorage = new TestStorage(newSyntaxTree)

    val migrationEngine = testStorage.migrationEngine

    val expected = Vector(RenameModel("Todo", "Todo1"))

    assert(
      migrationEngine
        .inferedMigrationSteps(newSyntaxTree, prevSyntaxTree, Map.empty)
        .getOrElse(fail()) == expected
    )
  }

  test(
    "Changing field types without type transformer hooks nor existing data in a migration works"
  ) {

    val prevCode = """
    @user
    @1 model User {
      @1 id: String @uuid
      @2 username: String @primary @publicCredential
      @3 password: String @secretCredential
      @4 isVerified: Boolean = false
      @5 todos: [Todo]
    }

    @2 model Todo {
      @1 title: String @primary
    }

    @3 model Admin {
      @1 username: String @primary @publicCredential
      @2 password: String @secretCredential
    }

    config { projectName = "test" }
    """
    val prevSyntaxTree = SyntaxTree.from(prevCode).get

    val code = """
    @user
    @1 model User {
      @1 id: String @uuid
      @2 username: String @primary @publicCredential
      @3 password: String @secretCredential
      @4 isVerified: Int = 0
      @5 todos: [Todo]
    }

    @2 model Todo {
      @1 title: String @primary
    }

    @3 model Admin {
      @1 username: String @primary @publicCredential
      @2 password: String @secretCredential
    }

    config { projectName = "test" }
    """
    val newSyntaxTree = SyntaxTree.from(code).get

    val testStorage = new TestStorage(newSyntaxTree)

    val migrationEngine = testStorage.migrationEngine

    assertThrows[UserError](
      migrationEngine
        .inferedMigrationSteps(
          newSyntaxTree,
          prevSyntaxTree,
          Map.empty.withDefaultValue(false)
        )
        .toTry
        .get
        .head
        .asInstanceOf[ChangeFieldTypes]
        .changes
        .head
    )
  }

  test(
    "Changing field types with type transformer hooks in a migration works"
  ) {

    val prevCode = """
    @user
    @1 model User {
      @1 id: String @uuid
      @2 username: String @primary @publicCredential
      @3 password: String @secretCredential
      @4 isVerified: Boolean = false
      @5 todos: [Todo]
    }

    @2 model Todo {
      @1 title: String @primary
    }

    @3 model Admin {
      @1 username: String @primary @publicCredential
      @2 password: String @secretCredential
    }

    config { projectName = "test" }
    """
    val prevSyntaxTree = SyntaxTree.from(prevCode).get

    val code = """

    @user
    @1 model User {
      @1 id: String @uuid
      @2 username: String @primary @publicCredential
      @3 password: String @secretCredential
      @4 isVerified: Int = 0
      @5 todos: [Todo]
    }

    @2 model Todo {
      @1 title: String @primary
    }

    @3 model Admin {
      @1 username: String @primary @publicCredential
      @2 password: String @secretCredential
    }

    config { projectName = "test" }
    """
    val newSyntaxTree = SyntaxTree.from(code).get

    val testStorage = new TestStorage(newSyntaxTree)

    val migrationEngine = testStorage.migrationEngine

    assertThrows[UserError](
      migrationEngine
        .inferedMigrationSteps(
          newSyntaxTree,
          prevSyntaxTree,
          Map.empty.withDefaultValue(false)
        )
        .toTry
        .get
        .head
        .asInstanceOf[ChangeFieldTypes]
        .changes
        .head
    )
  }
}
