ThisBuild / scalaVersion := "2.13.2"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "com.pragmalang"
ThisBuild / organizationName := "pragma"

lazy val root = (project in file("."))
  .settings(
    name := "pragma",
    maintainer := "Anas Al-Barghouthy @anasbarg, Muhammad Tabaza @Tabzz98",
    packageSummary := "A language for building GraphQL APIs",
    packageDescription := "See https://docs.pragmalang.com for details.",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.0.8" % Test,
      "com.lihaoyi" %% "pprint" % "0.5.6" % Test
    )
  )

scalacOptions ++= Seq("-feature", "-deprecation", "-Xlint:unused")

libraryDependencies ++= Seq(
  "org.parboiled" %% "parboiled" % "2.2.0",
  "org.sangria-graphql" %% "sangria" % "2.0.0",
  "io.spray" %% "spray-json" % "1.3.5",
  "org.sangria-graphql" %% "sangria-spray-json" % "1.0.2",
  "com.pauldijou" %% "jwt-core" % "4.3.0",
  "org.http4s" %% "http4s-dsl" % "0.21.4",
  "org.http4s" %% "http4s-blaze-server" % "0.21.4",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "org.typelevel" %% "cats-effect" % "2.1.3",
  "org.typelevel" %% "cats-core" % "2.1.1",
  "org.tpolecat" %% "doobie-core" % "0.9.0",
  "org.tpolecat" %% "doobie-hikari" % "0.9.0",
  "org.postgresql" % "postgresql" % "42.2.14"
)

enablePlugins(GraalVMNativeImagePlugin, DockerComposePlugin)

// Requires `native-image` utility from Graal
// Run `gu install native-image` to install it (`gu` comes with Graal)
// Run `sbt graalvm-native-image:packageBin` to generate native binary
// See: https://www.scala-sbt.org/sbt-native-packager/index.html
graalVMNativeImageOptions := Seq(
  "--no-fallback",
  "--language:js",
  "--language:python",
  "--initialize-at-build-time=scala.runtime.Statics$VM"
)

// To make tests run within a Docker container
// (for Postgres)
// See https://github.com/Tapad/sbt-docker-compose
// NOTE: If the docker containers cannot be started
// it's most likely because the port 5433 is already in use.
// Run `docker ps` and then run `docker kill <postgres-containe-id>`
// to kill the postgres container to fix it.
addCommandAlias("test", "dockerComposeTest")
dockerImageCreationTask := (publishLocal in Docker).value
dockerExposedPorts ++= Seq(9000, 9000)
