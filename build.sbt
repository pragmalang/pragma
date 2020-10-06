ThisBuild / scalaVersion := "2.13.2"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "com.pragmalang"
ThisBuild / organizationName := "pragma"

import Dependencies._

lazy val commonScalacOptions = Seq(
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

lazy val core = (project in file("core"))
  .settings(
    name := "core",
    maintainer := "Anas Al-Barghouthy @anasbarg, Muhammad Tabaza @Tabzz98",
    packageSummary := "Core abstractions used by other Pragma modules",
    packageDescription := "See https://docs.pragmalang.com for details.",
    scalacOptions := commonScalacOptions,
    scalacOptions in (Compile, console) := Seq.empty,
    libraryDependencies ++= testDependencies ++ Seq(cats, spray, parboiled)
  )

lazy val daemon = (project in file("daemon"))
  .settings(
    name := "pragmad",
    maintainer := "Anas Al-Barghouthy @anasbarg, Muhammad Tabaza @Tabzz98",
    packageSummary := "The daemon for Pragmalang",
    packageDescription := "See https://docs.pragmalang.com for details.",
    scalacOptions := commonScalacOptions,
    scalacOptions in (Compile, console) := Seq.empty,
    libraryDependencies ++= testDependencies ++ Seq(
      cats,
      catsEffect,
      doobieCore,
      doobieHikari,
      doobiePostgres,
      bcrypt,
      jwtCore,
      sangria,
      sangriaSpray,
      http4sDsl,
      http4sBlazeServer,
      http4sBlazeClient,
      logbackClassic,
      kebsSprayJson
    ),
    mainClass in assembly := Some("com.pragmalang.Main"),
    test in assembly := {},
    composeNoBuild := true
  )
  .dependsOn(core)
  .enablePlugins(DockerComposePlugin, DockerPlugin)

lazy val pragmaCLI = (project in file("cli"))
  .settings(
    name := "pragma",
    maintainer := "Anas Al-Barghouthy @anasbarg, Muhammad Tabaza @Tabzz98",
    packageSummary := "The CLI for Pragmalang",
    packageDescription := "See https://docs.pragmalang.com for details.",
    scalacOptions := commonScalacOptions,
    scalacOptions in (Compile, console) := Seq.empty,
    libraryDependencies ++= testDependencies ++ Seq(scopt, osLib, catsEffect),
    mainClass in assembly := Some("com.pragmalang.Main"),
    test in assembly := {}
  )
  .dependsOn(core)

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

/*
  Apache Bench benchmark:
  Run the ammonite script in `test/benchmark`:
  `amm PragmaBench.sc`
  Make sure to have the server and the database running
  before running the benchmark:
  `dockerComposeUp;run "dev" "./src/test/benchmark/montajlink.pragma"`
  NOTE: Apache Bench must be installed:
  `sudo apt install apache2-utils`
 */

/*
  For packaging Pragma using Docker:
  `docker:publishLocal`
 */
dockerBaseImage := "oracle/graalvm-ce:latest"
dockerExposedPorts := Seq(3030)
