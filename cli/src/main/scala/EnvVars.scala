package cli

import cats.implicits._
import pragma.envUtils._

object EnvVars {
  val PRAGMA_PG_USER = EnvVarDef(
    name = "PRAGMA_PG_USER",
    description = "PostgreSQL DB username",
    isRequired = true
  )
  val PRAGMA_PG_PASSWORD = EnvVarDef(
    name = "PRAGMA_PG_PASSWORD",
    description = "PostgreSQL DB password",
    isRequired = true
  )
  val PRAGMA_PG_HOST = EnvVarDef(
    name = "PRAGMA_PG_HOST",
    description = "PostgreSQL DB host",
    isRequired = true
  )
  val PRAGMA_PG_PORT = EnvVarDef(
    name = "PRAGMA_PG_PORT",
    description = "PostgreSQL DB port",
    isRequired = true
  )
  val PRAGMA_PG_DB_NAME = EnvVarDef(
    name = "PRAGMA_PG_DB_NAME",
    description = "PostgreSQL DB name",
    isRequired = true
  )

  val PRAGMA_HOSTNAME = EnvVarDef(
    name = "PRAGMA_HOSTNAME",
    description = "Pragma Daemon hostname",
    isRequired = true,
    defaultValue = _ => "localhost".some
  )

  val PRAGMA_SECRET = EnvVarDef(
    name = "PRAGMA_SECRET",
    description = "Pragma secret",
    isRequired = true,
    defaultValue = mode =>
      mode match {
        case pragma.RunMode.Dev =>
          Some("DUMMY_SECRET")
        case pragma.RunMode.Prod => None
      }
  )

  val PRAGMA_PORT = EnvVarDef(
    name = "PRAGMA_PORT",
    description = "Pragma Daemon port",
    isRequired = true,
    defaultValue = _ => "9584".some
  )

  val envVarsDefs = List(
    PRAGMA_PG_USER,
    PRAGMA_PG_PASSWORD,
    PRAGMA_PG_HOST,
    PRAGMA_PG_PORT,
    PRAGMA_PG_DB_NAME,
    PRAGMA_HOSTNAME,
    PRAGMA_PORT,
    PRAGMA_SECRET
  )
}
