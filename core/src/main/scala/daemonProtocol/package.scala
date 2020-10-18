package pragma.daemonProtocol

import spray.json._
import pl.iterators.kebs.json._

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
    secret: String,
    pgUri: String,
    pgUser: String,
    pgPassword: String,
    previousMigration: Option[Migration]
)

case class ProjectInput(
    name: String,
    secret: String,
    pgUri: String,
    pgUser: String,
    pgPassword: String
)

package object DaemonJsonProtocol extends DefaultJsonProtocol with KebsSpray
