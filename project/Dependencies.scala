import sbt._

object Dependencies {
  lazy val cats = "org.typelevel" %% "cats-core" % "2.0.0"
  lazy val catsEffect = "org.typelevel" %% "cats-effect" % "2.1.3"

  lazy val parboiled = "org.parboiled" %% "parboiled" % "2.2.0"

  lazy val spray = "io.spray" %% "spray-json" % "1.3.5"

  lazy val sangria = "org.sangria-graphql" %% "sangria" % "2.0.0"
  lazy val sangriaSpray = "org.sangria-graphql" %% "sangria-spray-json" % "1.0.2"

  lazy val jwtCore = "com.pauldijou" %% "jwt-core" % "4.3.0"

  lazy val http4sVersion = "0.21.6"
  lazy val http4sDsl = "org.http4s" %% "http4s-dsl" % http4sVersion
  lazy val http4sBlazeServer = "org.http4s" %% "http4s-blaze-server" % http4sVersion
  lazy val http4sBlazeClient = "org.http4s" %% "http4s-blaze-client" % http4sVersion
  lazy val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.2.3"

  lazy val doobieVersion = "0.9.0"
  lazy val doobieCore = "org.tpolecat" %% "doobie-core" % doobieVersion
  lazy val doobieHikari = "org.tpolecat" %% "doobie-hikari" % doobieVersion
  lazy val doobiePostgres = "org.tpolecat" %% "doobie-postgres" % doobieVersion

  lazy val osLib = "com.lihaoyi" %% "os-lib" % "0.7.1"

  lazy val requests = "com.lihaoyi" %% "requests" % "0.6.5"

  lazy val scopt = "com.github.scopt" %% "scopt" % "4.0.0"

  lazy val bcrypt = "com.github.t3hnar" %% "scala-bcrypt" % "4.3.0"

  lazy val testDependencies = Seq(
    "org.scalatest" %% "scalatest" % "3.0.8" % Test,
    "com.lihaoyi" %% "pprint" % "0.5.6" % Test,
    "org.tpolecat" %% "doobie-scalatest" % doobieVersion % Test
  )

  lazy val kebsSprayJson = "pl.iterators" %% "kebs-spray-json" % "1.8.1"

  lazy val metacall = RootProject(uri("git://github.com/metacall/scala-port.git"))

}
