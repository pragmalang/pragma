ThisBuild / scalaVersion := "2.13.2"
ThisBuild / version := "0.0.1"
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
    scalacOptions := commonScalacOptions,
    scalacOptions in (Compile, console) := Seq.empty,
    libraryDependencies ++= testDependencies ++ Seq(
      cats,
      spray,
      parboiled,
      kebsSprayJson
    )
  )

lazy val daemon = (project in file("daemon"))
  .settings(
    name := "pragmad",
    maintainer := "Anas Al-Barghouthy @anasbarg, Muhammad Tabaza @Tabzz98",
    packageSummary := "The daemon for Pragmalang",
    packageDescription := "See https://docs.pragmalang.com for details.",
    scalacOptions := commonScalacOptions,
    scalacOptions in (Compile, console) := Seq.empty,
    parallelExecution in Test := false,
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
      logbackClassic
    ),
    dockerRepository in Docker := Some("pragmalang"),
    dockerExposedPorts := Seq(3030),
    dockerUpdateLatest := true
  )
  .dependsOn(core)
  .enablePlugins(
    DockerComposePlugin,
    JavaAppPackaging,
    DockerPlugin
  )

lazy val cli = (project in file("cli"))
  .settings(
    name := "pragma",
    maintainer := "Anas Al-Barghouthy @anasbarg, Muhammad Tabaza @Tabzz98",
    packageSummary := "The CLI for Pragmalang",
    packageDescription := "See https://docs.pragmalang.com for details.",
    scalacOptions := commonScalacOptions,
    scalacOptions in (Compile, console) := Seq.empty,
    libraryDependencies ++= testDependencies ++ Seq(
      scopt,
      osLib,
      requests
    ),
    graalVMNativeImageGraalVersion := Some("20.1.0-java11"),
    graalVMNativeImageOptions := Seq(
      "--static",
      "--no-fallback",
      "--allow-incomplete-classpath",
      "--initialize-at-build-time=scala.runtime.Statics$VM",
      "-H:+ReportExceptionStackTraces",
      "-H:+AddAllCharsets",
      "-H:IncludeResources=.*docker-compose.yml",
      "--enable-http",
      "--enable-https",
      "--enable-all-security-services"
    ),
    wixProductId := "0e5e2980-bf07-4bf0-b446-2cfb4bf4704a",
    wixProductUpgradeId := "5603913d-7bde-46eb-ac47-44ed2cb4fd08"
  )
  .dependsOn(core)
  .enablePlugins(GraalVMNativeImagePlugin, UniversalPlugin, WindowsPlugin)
