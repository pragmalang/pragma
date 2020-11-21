package pragma.daemonProtocol

case class Migration(
    id: String,
    code: String,
    functions: List[ImportedFunction]
)

case class MigrationInput(
    code: String,
    functions: List[ImportedFunctionInput]
)

case class ImportedFunction(
    id: String,
    name: String,
    scopeName: String,
    content: String,
    runtime: String,
    binary: Boolean
)

case class ImportedFunctionInput(
    name: String,
    scopeName: String,
    content: String,
    runtime: String,
    binary: Boolean
)

case class Project(
    name: String,
    secret: Option[String],
    pgUri: Option[String],
    pgUser: Option[String],
    pgPassword: Option[String],
    previousDevMigration: Option[Migration],
    previousProdMigration: Option[Migration]
)

case class ProjectInput(
    name: String,
    secret: Option[String] = None,
    pgUri: Option[String] = None,
    pgUser: Option[String] = None,
    pgPassword: Option[String] = None
)
