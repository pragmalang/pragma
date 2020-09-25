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
      "com.lihaoyi" %% "pprint" % "0.5.6" % Test,
      "org.tpolecat" %% "doobie-scalatest" % "0.9.0"
    )
  )

scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-Wunused:imports,patvars,privates,locals,explicits,implicits,params",
  "-Xlint",
  "-explaintypes",
  "-Wdead-code",
  "-Wextra-implicit",
  "-Wnumeric-widen",
  "-Wself-implicit"
)

// To suppress warnings in `sbt console`
scalacOptions in (Compile, console) := Seq.empty

libraryDependencies ++= Seq(
  "org.parboiled" %% "parboiled" % "2.2.0",
  "org.sangria-graphql" %% "sangria" % "2.0.0",
  "io.spray" %% "spray-json" % "1.3.5",
  "com.pauldijou" %% "jwt-core" % "4.3.0",
  "org.http4s" %% "http4s-dsl" % "0.21.6",
  "org.http4s" %% "http4s-blaze-server" % "0.21.6",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "org.typelevel" %% "cats-effect" % "2.1.3",
  "org.tpolecat" %% "doobie-core" % "0.9.0",
  "org.tpolecat" %% "doobie-hikari" % "0.9.0",
  "org.tpolecat" %% "doobie-postgres" % "0.9.0",
  "com.lihaoyi" %% "os-lib" % "0.7.1",
  "com.github.scopt" %% "scopt" % "3.7.1",
  "org.sangria-graphql" %% "sangria-spray-json" % "1.0.2",
  "com.github.t3hnar" %% "scala-bcrypt" % "4.3.0"
)

enablePlugins(DockerComposePlugin, GraalVMNativeImagePlugin)

/*
  GraalVM Native Image Generation:
  Requires `native-image` utility from Graal
  Run `gu install native-image` to install it (`gu` comes with Graal)
  Run `sbt graalvm-native-image:packageBin` to generate native binary
  See: https://www.scala-sbt.org/sbt-native-packager/index.html
  To generate META-INF:
  java -agentlib:native-image-agent=config-merge-dir="./src/main/resources/META-INF/native-image/",config-write-initial-delay-secs=0 -jar "./target/scala-2.13/<pragma-jar>" dev <pragmafile>
  See https://www.graalvm.org/reference-manual/native-image/Configuration/#assisted-configuration-of-native-image-builds
      https://noelwelsh.com/posts/2020-02-06-serverless-scala-services.html

  Also: Make sure everthing else other than this process is canceled. It needs all the memory it can get.
 */

/*
  To run tests within a Docker container (for Postgres):
  `sbt dockerComposeTest`
  See https://github.com/Tapad/sbt-docker-compose
  NOTE: If the docker containers cannot be started
  it's most likely because the port 5433 is already in use.
  Run `docker ps` and then run `docker kill <postgres-containe-id>`
  to kill the postgres container to fix it.
 */
composeNoBuild := true

/*
  Apache Bench benchmark:
  Run the ammonite script in `test/benchmark`:
  `amm PragmaBench.sc`
  Make sure to have the server and the database running 
  before running the benchmark:
  `dockerComposeUp;run "dev" "./src/test/benchmark/montajlink.pragma"`
  NOTE: Apache Bench must be installed:
  `sudo apt install ab`
*/