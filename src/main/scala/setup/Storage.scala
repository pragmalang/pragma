package setup
import sangria.ast.Document
import domain.SyntaxTree
import running.Request

trait Storage extends WithSyntaxTree {
  override val syntaxTree: SyntaxTree
  val migrator: Migrator

  def runQuery(query: Document): Request
  def migrate() = migrator.syntaxTree(syntaxTree).run()
  def dockerComposeYaml(): String
}

case class PrismaMongo(syntaxTree: SyntaxTree) extends Storage {
  val converter: Converter = GraphQlConverter(syntaxTree)
  override val migrator: Migrator = PrismaMongoMigrator(Some(syntaxTree))
  override def runQuery(query: Document): Request = ???
  override def dockerComposeYaml() =
    """
version: '3'
services:
  prisma:
    image: prismagraphql/prisma:1.34
    restart: always
    ports:
      - '4466:4466'
    environment:
      PRISMA_CONFIG: |
        port: 4466
        managementApiSecret:
        databases:
          default:
            connector: mongo
            uri: mongodb://prisma:prisma@mongo-db
  mongo-db:
    image: mongo:3.6
    restart: always
    environment:
      MONGO_INITDB_ROOT_USERNAME: prisma
      MONGO_INITDB_ROOT_PASSWORD: prisma
    ports:
      - '27017:27017'
    volumes:
      - mongo:/var/lib/mongo
volumes:
  mongo: ~
    """;
}
