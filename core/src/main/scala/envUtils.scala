package pragma.envUtils

import cats.implicits._

case class EnvVarDef(
    name: String,
    description: String,
    isRequired: Boolean,
    defaultValue: Option[String] = None
)
object EnvVarDef {
  private def envVarValue(name: String, defs: List[EnvVarDef]): Option[String] =
    defs.find(_.name == name) flatMap { definition =>
      sys.env.get(name) match {
        case Some(value) => value.some
        case None        => definition.defaultValue
      }
    }

  def parseEnvVars(
      defs: List[EnvVarDef]
  ): Either[::[EnvVarError], EnvVarDef => String] = {
    val errors = defs
      .filter { d =>
        (d.isRequired && !d.defaultValue.isDefined) && !sys.env.contains(d.name)
      }
      .map(v => EnvVarError.RequiredButNotFound(v))

    errors match {
      case Nil          => ((d: EnvVarDef) => envVarValue(d.name, defs).get).asRight
      case head :: tail => ::(head, tail).asLeft
    }
  }
}

sealed trait EnvVarError {
  val envVarDef: EnvVarDef
  val msg: String
}
object EnvVarError {
  case class RequiredButNotFound(envVarDef: EnvVarDef) extends EnvVarError {
    override val msg: String =
      s"Environment variable `${envVarDef.name}` is required, but not defined"
  }

  def render(errors: ::[EnvVarError]): String = {
    val isPlural = errors.length > 1
    val `variable/s` = if (isPlural) "variables" else "variable"
    val missingVarNames = errors.map(_.envVarDef.name).mkString(", ")
    val errMsg =
      s"""
      |Environment ${`variable/s`} $missingVarNames must be specified.
      """.stripMargin

    errMsg
  }
}

sealed trait EnvVarValue
object EnvVarValue {
  case class UserProvided(value: String) extends EnvVarValue
  case class DefaultedTo(value: String) extends EnvVarValue
}
