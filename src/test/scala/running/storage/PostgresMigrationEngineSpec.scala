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
    """
    val newSyntaxTree = SyntaxTree.from(code).get

    val migrationEngine = new PostgresMigrationEngine(newSyntaxTree)

    val expected = Vector(RenameModel("Todo", "Todo1"))

    assert(
      migrationEngine
        .inferedMigrationSteps(newSyntaxTree, prevSyntaxTree, (_, _) => false) == expected
    )
  }

  test(
    "Changing field types in a migration works"
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
    """
    val newSyntaxTree = SyntaxTree.from(code).get

    val migrationEngine = new PostgresMigrationEngine(newSyntaxTree)

    val expected = Vector(
      ChangeManyFieldTypes(
        PModel(
          "User",
          List(
            PModelField(
              "id",
              PString,
              None,
              1,
              List(
                Directive(
                  "uuid",
                  PInterfaceValue(Map(), PInterface("", List(), None)),
                  FieldDirective,
                  Some(PositionRange(Position(51, 4, 21), Position(56, 4, 26)))
                )
              ),
              Some(PositionRange(Position(40, 4, 10), Position(42, 4, 12)))
            ),
            PModelField(
              "username",
              PString,
              None,
              2,
              List(
                Directive(
                  "primary",
                  PInterfaceValue(Map(), PInterface("", List(), None)),
                  FieldDirective,
                  Some(PositionRange(Position(83, 5, 27), Position(91, 5, 35)))
                ),
                Directive(
                  "publicCredential",
                  PInterfaceValue(Map(), PInterface("", List(), None)),
                  FieldDirective,
                  Some(PositionRange(Position(92, 5, 36), Position(109, 5, 53)))
                )
              ),
              Some(PositionRange(Position(66, 5, 10), Position(74, 5, 18)))
            ),
            PModelField(
              "password",
              PString,
              None,
              3,
              List(
                Directive(
                  "secretCredential",
                  PInterfaceValue(Map(), PInterface("", List(), None)),
                  FieldDirective,
                  Some(
                    PositionRange(Position(136, 6, 27), Position(153, 6, 44))
                  )
                )
              ),
              Some(PositionRange(Position(119, 6, 10), Position(127, 6, 18)))
            ),
            PModelField(
              "isVerified",
              PBool,
              Some(PBoolValue(false)),
              4,
              List(),
              Some(PositionRange(Position(163, 7, 10), Position(173, 7, 20)))
            ),
            PModelField(
              "todos",
              PArray(PReference("Todo")),
              None,
              5,
              List(),
              Some(PositionRange(Position(200, 8, 10), Position(205, 8, 15)))
            )
          ),
          List(
            Directive(
              "user",
              PInterfaceValue(Map(), PInterface("", List(), None)),
              ModelDirective,
              Some(PositionRange(Position(5, 2, 5), Position(10, 2, 10)))
            )
          ),
          1,
          Some(PositionRange(Position(24, 3, 14), Position(28, 3, 18)))
        ),
        Vector(
          ChangeFieldType(
            PModelField(
              "isVerified",
              PBool,
              Some(PBoolValue(false)),
              4,
              List(),
              Some(PositionRange(Position(163, 7, 10), Position(173, 7, 20)))
            ),
            PInt,
            None,
            None
          )
        )
      )
    )

    assert(
      migrationEngine
        .inferedMigrationSteps(newSyntaxTree, prevSyntaxTree, (_, _) => false) == expected
    )
  }
}
