package setup.storage.postgres

import setup._
import setup.storage.postgres._
import org.scalatest._
import setup.storage.postgres.SQLMigrationStep._
import org.jooq.util.postgres.PostgresDataType
import domain.SyntaxTree
import setup.storage.postgres.AlterTableAction._

class PostgresMigrationEngineSpec extends FunSuite {
  test("`Iterable[SQLMigrationStep]#renderSQL` works") {
    val columns =
      ColumnDefinition(
        name = "username",
        dataType = PostgresDataType.TEXT,
        isNotNull = true,
        isAutoIncrement = false,
        isPrimaryKey = true,
        isUUID = false,
        isUnique = false,
        foreignKey = None
      ) :: ColumnDefinition(
        name = "password",
        dataType = PostgresDataType.TEXT,
        isNotNull = true,
        isAutoIncrement = false,
        isPrimaryKey = false,
        isUUID = false,
        isUnique = false,
        foreignKey = None
      ) :: ColumnDefinition(
        name = "myFriend",
        dataType = PostgresDataType.TEXT,
        isNotNull = true,
        isAutoIncrement = false,
        isPrimaryKey = false,
        isUUID = false,
        isUnique = false,
        foreignKey = None
      ) :: ColumnDefinition(
        name = "uuid",
        dataType = PostgresDataType.UUID,
        isNotNull = true,
        isAutoIncrement = false,
        isPrimaryKey = false,
        isUUID = true,
        isUnique = true,
        foreignKey = None
      ) :: Nil
    val createTable = CreateTable("Users", columns.toVector)
    val renameTable = RenameTable("Users", "User")
    val addColumn = AlterTable(
      "User",
      AlterTableAction.AddColumn(
        ColumnDefinition(
          name = "age1",
          dataType = PostgresDataType.INT8,
          isNotNull = true,
          isAutoIncrement = false,
          isPrimaryKey = false,
          isUUID = false,
          isUnique = false,
          foreignKey = None
        )
      )
    )
    val renameColumn =
      AlterTable("User", AlterTableAction.RenameColumn("age1", "age"))
    val changeColumnType = AlterTable(
      "User",
      AlterTableAction.ChangeColumnType("age", PostgresDataType.INT)
    )
    val addFk = AlterTable(
      "User",
      AlterTableAction.AddForeignKey("User", "username", "myFriend")
    )
    val dropColumn =
      AlterTable("User", AlterTableAction.DropColumn("age", true))
    val dropTable = DropTable("User")

    val expected =
      """|CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
         |
         |CREATE TABLE IF NOT EXISTS "Users"(
         |"username" text NOT NULL PRIMARY KEY,
         |"password" text NOT NULL,
         |"myFriend" text NOT NULL,
         |"uuid" uuid NOT NULL UNIQUE DEFAULT uuid_generate_v4 ());
         |
         |
         |ALTER TABLE "Users" RENAME TO "User";
         |
         |ALTER TABLE "User" ADD COLUMN "age1" int8 NOT NULL;
         |
         |ALTER TABLE "User" RENAME COLUMN "age1" TO "age";
         |
         |ALTER TABLE "User" ALTER COLUMN "age" TYPE int;
         |
         |ALTER TABLE "User" ADD FOREIGN KEY ("myFriend") REFERENCES "User"("username");
         |
         |ALTER TABLE "User" DROP COLUMN IF EXISTS "age";
         |
         |DROP TABLE IF EXISTS "User";""".stripMargin

    assert(
      expected ==
        PostgresMigration(
          Vector(
            createTable,
            renameTable,
            addColumn,
            renameColumn,
            changeColumnType,
            addFk,
            dropColumn,
            dropTable
          ),
          Vector.empty
        ).renderSQL
    )
  }

  test("PostgresMigrationEngine#migration works") {
    val code = """
    @1 @user
    model User {
      id: String @uuid
      username: String @primary @publicCredential
      password: String @secretCredential
      isVerified: Boolean = false
      todo: Todo?
    }

    @2 model Todo {
      title: String
    }
    """
    val syntaxTree = SyntaxTree.from(code).get
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
              "_id",
              PostgresDataType.TEXT,
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
              PostgresDataType.TEXT,
              true,
              false,
              false,
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
              PostgresDataType.UUID,
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
              PostgresDataType.TEXT,
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
              PostgresDataType.TEXT,
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
              PostgresDataType.BOOL,
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
              "todo",
              PostgresDataType.TEXT,
              false,
              false,
              false,
              false,
              false,
              Some(ForeignKey("Todo", "_id"))
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

    assert(postgresMigration == expected)
  }
}
