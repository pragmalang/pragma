package setup.storage.postgres

import setup._
import setup.storage.postgres._
import org.scalatest._
import setup.storage.postgres.SQLMigrationStep._
import domain.SyntaxTree
import setup.storage.postgres.AlterTableAction._

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

  test("`Iterable[SQLMigrationStep]#renderSQL` works") {

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
       |"source_User" TEXT NOT NULL REFERENCES "User"("username"),
       |"target_Todo" TEXT NOT NULL REFERENCES "Todo"("title"));
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
              Some(ForeignKey("User", "username"))
            ),
            ColumnDefinition(
              "target_Todo",
              PostgresType.TEXT,
              true,
              false,
              false,
              false,
              false,
              Some(ForeignKey("Todo", "title"))
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
}
