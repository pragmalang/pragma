ThisBuild / scalaVersion := "2.13.4"
ThisBuild / version := "0.3.2"
ThisBuild / organization := "com.pragmalang"
ThisBuild / organizationName := "pragma"

import Dependencies._
import Tests._


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
    ),
    test in assembly := {}
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
    dockerUpdateLatest := true,
    fork in run := true,
    fork in Test := true,
    testGrouping in Test := (testGrouping in Test).value.flatMap { group =>
      group.tests map (test => Group(test.name, Seq(test), SubProcess(ForkOptions())))
    },
    test in assembly := {}
  )
  .dependsOn(core, metacall)
  .enablePlugins(
    DockerComposePlugin,
    JavaAppPackaging,
    DockerPlugin
  )

val writeVersion =
  taskKey[File]("Write build version to resources directory as `version.json`")

val cli = (project in file("cli"))
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
    writeVersion := {
      import java.io.FileOutputStream
      val versionFile =
        new File((Compile / resourceDirectory).value.getAbsolutePath(), "version.json")
      val out = new FileOutputStream(versionFile)
      out.write(('"' + version.value + '"').getBytes())
      out.close()
      versionFile
    },
    (Compile / compile) := ((Compile / compile) dependsOn writeVersion).value,
    jlinkIgnoreMissingDependency := JlinkIgnore.everything,
    rpmVendor := organizationName.value,
    rpmLicense := Some("Apache 2.0"),
    wixProductId := "0e5e2980-bf07-4bf0-b446-2cfb4bf4704a",
    wixProductUpgradeId := "5603913d-7bde-46eb-ac47-44ed2cb4fd08",
    sources in (Compile, doc) := Seq.empty,
    publishArtifact in (Compile, packageDoc) := false,
    target in assembly := file("./cli/target/"),
    test in assembly := {}
  )
  .dependsOn(core)
  .dependsOn(daemon)
  .enablePlugins(
    UniversalPlugin,
    JlinkPlugin,
    WindowsPlugin,
    LinuxPlugin,
    DebianPlugin,
    RpmPlugin
  )
