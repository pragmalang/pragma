ThisBuild / scalaVersion := "2.13.4"
ThisBuild / version := "0.1.0"
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
  "-Wconf:cat=lint-byname-implicit:silent"
)

lazy val core = (project in file("core"))
  .settings(
    name := "core",
    scalacOptions := commonScalacOptions,
    scalacOptions in (Compile, console) := Seq.empty,
    sources in (Compile, doc) := Seq.empty,
    publishArtifact in (Compile, packageDoc) := false,
    libraryDependencies ++= testDependencies ++ Seq(
      cats,
      spray,
      parboiled,
      kebsSprayJson,
      jwtCore
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
    packageName := name.value,
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
    jlinkIgnoreMissingDependency := JlinkIgnore.everything,
    wixProductId := "0e5e2980-bf07-4bf0-b446-2cfb4bf4704a",
    wixProductUpgradeId := "5603913d-7bde-46eb-ac47-44ed2cb4fd08",
    name in Windows := s"${name.value}-${version.value}",
    wixPackageInfo := com.typesafe.sbt.packager.windows.WindowsProductInfo(
      id = wixProductId.value,
      title = "Pragma CLI",
      version = version.value,
      maintainer = "Muhammad Tabaza @Tabzz98, Anas H Al-Barghouthy @anasbarg",
      description = packageSummary.value,
      upgradeId = wixProductUpgradeId.value
    ),
    sources in (Compile, doc) := Seq.empty,
    publishArtifact in (Compile, packageDoc) := false
  )
  .dependsOn(core)
  .enablePlugins(
    UniversalPlugin,
    JlinkPlugin,
    WindowsPlugin,
    LinuxPlugin,
    DebianPlugin,
    RpmPlugin
  )
