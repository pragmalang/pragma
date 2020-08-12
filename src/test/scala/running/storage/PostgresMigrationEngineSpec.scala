package running.storage.postgres

import running.storage._
import running.storage.postgres._
import org.scalatest._, funsuite.AnyFunSuite
import SQLMigrationStep._
import domain.SyntaxTree
import AlterTableAction._
import OnDeleteAction.Cascade

import domain._
import org.parboiled2.Position

import doobie._
import doobie.implicits._
import cats._
import cats.effect._
import cats.implicits._

class PostgresMigrationEngineSpec extends AnyFunSuite {

  implicit val cs = IO.contextShift(ExecutionContexts.synchronous)

  def transactorFromDbName(dbName: String) =
    Transactor.fromDriverManager[IO](
      "org.postgresql.Driver",
      s"jdbc:postgresql://localhost:5433/${dbName}",
      "test",
      "test",
      Blocker.liftExecutionContext(ExecutionContexts.synchronous)
    )

  def createDatabase(dbName: String) =
    Fragment(s"""CREATE DATABASE ${dbName} OWNER test;""", Nil, None).update

  def removeAllTablesFromDb(dbName: String): IO[Unit] =
    sql"""
      DROP SCHEMA public CASCADE;
      CREATE SCHEMA public;
      GRANT ALL ON SCHEMA public TO test;
      GRANT ALL ON SCHEMA public TO public;
    """.update.run.void.transact(transactorFromDbName(dbName))

  test("`PostgresMigration#renderSQL` works") {
    val code = """
        @1 @user
        model User_renderSQL {
          @1 id: String @uuid
          @2 username: String @primary @publicCredential
          @3 password: String @secretCredential
          @4 isVerified: Boolean = false
          @5 todos: [Todo_renderSQL]
        }
    
        @2 model Todo_renderSQL {
          @1 title: String @primary
        }
        """
    val syntaxTree = SyntaxTree.from(code).get

    val migrationEngine = new PostgresMigrationEngine[IO](syntaxTree)

    val transactor = transactorFromDbName("test")

    val expected =
      Some(
        """|CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
       |
       |CREATE TABLE IF NOT EXISTS "Todo_renderSQL"(
       |);
       |
       |
       |CREATE TABLE IF NOT EXISTS "User_renderSQL"(
       |);
       |
       |
       |ALTER TABLE "Todo_renderSQL" ADD COLUMN "title" TEXT NOT NULL PRIMARY KEY;
       |
       |ALTER TABLE "User_renderSQL" ADD COLUMN "username" TEXT NOT NULL PRIMARY KEY;
       |
       |ALTER TABLE "User_renderSQL" ADD COLUMN "id" UUID NOT NULL DEFAULT uuid_generate_v4 ();
       |
       |ALTER TABLE "User_renderSQL" ADD COLUMN "password" TEXT NOT NULL;
       |
       |ALTER TABLE "User_renderSQL" ADD COLUMN "isVerified" BOOL NOT NULL;
       |
       |CREATE TABLE IF NOT EXISTS "User_renderSQL_todos"(
       |"source_User_renderSQL" TEXT NOT NULL REFERENCES "User_renderSQL"("username") ON DELETE CASCADE,
       |"target_Todo_renderSQL" TEXT NOT NULL REFERENCES "Todo_renderSQL"("title") ON DELETE CASCADE);
       |""".stripMargin
      )

    assert(
      expected == (migrationEngine.initialMigration match {
        case Left(e) => throw e
        case Right(migration) => migration.renderSQL
      }) 
    )

    migrationEngine.initialMigration.getOrElse(fail())
      .run(transactor)
      .transact(transactor)
      .unsafeRunSync()
  }

  test("PostgresMigrationEngine#migration works") {

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

    val createTodoModel = CreateModel(syntaxTree.modelsById("Todo"))
    val createUserModel = CreateModel(syntaxTree.modelsById("User"))

    val expected = Vector(
      CreateTable("User", Vector()),
      CreateTable("Todo", Vector()),
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
    )

    val postgresMigration = PostgresMigration(
      Vector(createTodoModel, createUserModel),
      SyntaxTree.empty,
      syntaxTree
    )

    assert(
      postgresMigration.sqlSteps == expected
    )
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

    val migrationEngine = new PostgresMigrationEngine[Id](newSyntaxTree)

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

    val migrationEngine = new PostgresMigrationEngine[Id](newSyntaxTree)

    val expected = Vector(DropTable("Admin"))
    assert(
      migrationEngine
        .migration(prevSyntaxTree, Map.empty)
        .getOrElse(fail())
        .sqlSteps == expected
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

    val migrationEngine = new PostgresMigrationEngine[Id](newSyntaxTree)

    val expected = Vector(RenameTable("Admin", "Admin1"))
    assert(
      migrationEngine
        .migration(prevSyntaxTree, Map.empty)
        .getOrElse(fail())
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

    val migrationEngine = new PostgresMigrationEngine[Id](newSyntaxTree)

    val expected =
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
      )

    assert(
      migrationEngine
        .migration(prevSyntaxTree, Map.empty)
        .getOrElse(fail())
        .sqlSteps == expected
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

    val migrationEngine = new PostgresMigrationEngine[Id](newSyntaxTree)

    val expected = Vector(AlterTable("Admin", DropColumn("password", true)))
    assert(
      migrationEngine
        .migration(prevSyntaxTree, Map.empty)
        .getOrElse(fail())
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

    val migrationEngine = new PostgresMigrationEngine[Id](newSyntaxTree)

    val expected =
      Vector(AlterTable("Admin", RenameColumn("password", "passcode")))
    assert(
      migrationEngine
        .migration(prevSyntaxTree, Map.empty)
        .getOrElse(fail())
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

    val migrationEngine = new PostgresMigrationEngine[Id](newSyntaxTree)

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

    val migrationEngine = new PostgresMigrationEngine[Id](newSyntaxTree)

    val expected = ChangeFieldType(
      PModelField(
        "isVerified",
        PBool,
        Some(PBoolValue(false)),
        4,
        List(),
        Some(PositionRange(Position(163, 7, 10), Position(173, 7, 20)))
      ),
      PInt,
      None
    )

    assert(
      migrationEngine
        .inferedMigrationSteps(newSyntaxTree, prevSyntaxTree, Map.empty)
        .getOrElse(fail())
        .head
        .asInstanceOf[ChangeManyFieldTypes]
        .changes
        .head == expected
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

    val migrationEngine = new PostgresMigrationEngine[Id](newSyntaxTree)

    val expected = ChangeFieldType(
      PModelField(
        "isVerified",
        PBool,
        Some(PBoolValue(false)),
        4,
        List(),
        Some(PositionRange(Position(163, 7, 10), Position(173, 7, 20)))
      ),
      PInt,
      None
    )

    assert(
      migrationEngine
        .inferedMigrationSteps(newSyntaxTree, prevSyntaxTree, Map.empty)
        .getOrElse(fail())
        .head
        .asInstanceOf[ChangeManyFieldTypes]
        .changes
        .head == expected
    )
  }
}
