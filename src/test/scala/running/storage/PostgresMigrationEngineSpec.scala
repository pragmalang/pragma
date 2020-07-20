package running.storage.postgres

import running.storage._
import running.storage.postgres._
import org.scalatest._
import SQLMigrationStep._
import domain.SyntaxTree
import AlterTableAction._
import OnDeleteAction.Cascade

import domain._
import org.parboiled2.Position

class PostgresMigrationEngineSpec extends FunSuite {
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
    """
  val syntaxTree = SyntaxTree.from(code).get

  test("`PostgresMigration#renderSQL` works") {

    val migrationEngine = new PostgresMigrationEngine(syntaxTree)

    val expected =
      """|CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
       |
       |CREATE TABLE IF NOT EXISTS "Todo"(
       |);
       |
       |
       |CREATE TABLE IF NOT EXISTS "User"(
       |);
       |
       |
       |ALTER TABLE "Todo" ADD COLUMN "title" TEXT NOT NULL PRIMARY KEY;
       |
       |ALTER TABLE "User" ADD COLUMN "username" TEXT NOT NULL PRIMARY KEY;
       |
       |ALTER TABLE "User" ADD COLUMN "id" UUID NOT NULL DEFAULT uuid_generate_v4 ();
       |
       |ALTER TABLE "User" ADD COLUMN "password" TEXT NOT NULL;
       |
       |ALTER TABLE "User" ADD COLUMN "isVerified" BOOL NOT NULL;
       |
       |CREATE TABLE IF NOT EXISTS "User_todos"(
       |"source_User" TEXT NOT NULL REFERENCES "User"("username") ON DELETE CASCADE,
       |"target_Todo" TEXT NOT NULL REFERENCES "Todo"("title") ON DELETE CASCADE);
       |""".stripMargin

    assert(expected == migrationEngine.initialMigration.renderSQL(syntaxTree))
  }

  test("PostgresMigrationEngine#migration works") {

    val createTodoModel = CreateModel(syntaxTree.modelsById("Todo"))
    val createUserModel = CreateModel(syntaxTree.modelsById("User"))
    val migrationEngine = new PostgresMigrationEngine(syntaxTree)

    val expected = PostgresMigration(
      Vector(
        CreateTable("Todo", Vector()),
        AlterTable(
          "Todo",
          AddColumn(
            ColumnDefinition(
              "title",
              PostgresType.TEXT,
              true,
              false,
              true,
              false,
              false,
              None
            )
          )
        ),
        CreateTable("User", Vector()),
        AlterTable(
          "User",
          AddColumn(
            ColumnDefinition(
              "id",
              PostgresType.UUID,
              true,
              false,
              false,
              false,
              true,
              None
            )
          )
        ),
        AlterTable(
          "User",
          AddColumn(
            ColumnDefinition(
              "username",
              PostgresType.TEXT,
              true,
              false,
              true,
              false,
              false,
              None
            )
          )
        ),
        AlterTable(
          "User",
          AddColumn(
            ColumnDefinition(
              "password",
              PostgresType.TEXT,
              true,
              false,
              false,
              false,
              false,
              None
            )
          )
        ),
        AlterTable(
          "User",
          AddColumn(
            ColumnDefinition(
              "isVerified",
              PostgresType.BOOL,
              true,
              false,
              false,
              false,
              false,
              None
            )
          )
        ),
        CreateTable(
          "User_todos",
          Vector(
            ColumnDefinition(
              "source_User",
              PostgresType.TEXT,
              true,
              false,
              false,
              false,
              false,
              Some(ForeignKey("User", "username", Cascade))
            ),
            ColumnDefinition(
              "target_Todo",
              PostgresType.TEXT,
              true,
              false,
              false,
              false,
              false,
              Some(ForeignKey("Todo", "title", Cascade))
            )
          )
        )
      ),
      Vector()
    )

    val postgresMigration = migrationEngine.migration(
      createTodoModel
        :: createUserModel
        :: Nil
    )

    // pprint.pprintln(postgresMigration)

    assert(postgresMigration == expected)
  }

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
    """
    val newSyntaxTree = SyntaxTree.from(code).get

    val migrationEngine = new PostgresMigrationEngine(newSyntaxTree)

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
        .inferedMigrationSteps(newSyntaxTree, prevSyntaxTree, (_, _) => false) == expected
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
    """
    val newSyntaxTree = SyntaxTree.from(code).get

    val migrationEngine = new PostgresMigrationEngine(newSyntaxTree)

    val expected = PostgresMigration(Vector(DropTable("Admin")), Vector.empty)
    assert(
      migrationEngine
        .migration(prevSyntaxTree, (_, _) => false) == expected
    )
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
    """
    val newSyntaxTree = SyntaxTree.from(code).get

    val migrationEngine = new PostgresMigrationEngine(newSyntaxTree)

    val expected =
      PostgresMigration(Vector(RenameTable("Admin", "Admin1")), Vector.empty)
    assert(
      migrationEngine
        .migration(prevSyntaxTree, (_, _) => false) == expected
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

    @3 model Admin {
      @1 username: String @primary
    }
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
      @2 password: String @secretCredential
    }
    """
    val newSyntaxTree = SyntaxTree.from(code).get

    val migrationEngine = new PostgresMigrationEngine(newSyntaxTree)

    val expected = PostgresMigration(
      Vector(
        AlterTable(
          "Admin",
          AddColumn(
            ColumnDefinition(
              "password",
              PostgresType.TEXT,
              true,
              false,
              false,
              false,
              false,
              None
            )
          )
        )
      ),
      Vector()
    )
    assert(
      migrationEngine
        .migration(prevSyntaxTree, (_, _) => false) == expected
      // .renderSQL(prevSyntaxTree)
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

    @3 model Admin {
      @1 username: String @primary @publicCredential
      @2 password: String @secretCredential
    }
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
      @1 username: String @primary
    }
    """
    val newSyntaxTree = SyntaxTree.from(code).get

    val migrationEngine = new PostgresMigrationEngine(newSyntaxTree)

    val expected = PostgresMigration(
      Vector(AlterTable("Admin", DropColumn("password", true))),
      Vector()
    )
    assert(
      migrationEngine
        .migration(prevSyntaxTree, (_, _) => false) == expected
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
    """
    val newSyntaxTree = SyntaxTree.from(code).get

    val migrationEngine = new PostgresMigrationEngine(newSyntaxTree)

    val expected = PostgresMigration(
      Vector(AlterTable("Admin", RenameColumn("password", "passcode"))),
      Vector()
    )
    assert(
      migrationEngine
        .migration(prevSyntaxTree, (_, _) => false) == expected
    )
  }
}
