ThisBuild / scalaVersion := "2.13.1"
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

libraryDependencies ++= Seq(
  "org.parboiled" %% "parboiled" % "2.1.8",
  "org.sangria-graphql" %% "sangria" % "2.0.0-M1",
  "com.github.nscala-time" %% "nscala-time" % "2.22.0",
  "io.spray" %% "spray-json" % "1.3.5",
  "com.pauldijou" %% "jwt-core" % "4.1.0",
  "com.typesafe.akka" %% "akka-stream" % "2.6.1",
  "org.mongodb.scala" %% "mongo-scala-driver" % "4.0.1",
  "org.typelevel" %% "cats-effect" % "2.1.3",
  "org.typelevel" %% "cats-core" % "2.1.1",
  "org.typelevel" %% "kittens" % "2.1.0"
)

// Requires `native-image` utility from Graal
// Run `gu install native-image` to install it (`gu` comes with Graal)
// Run `sbt graalvm-native-image:packageBin` to generate native binary
// See: https://www.scala-sbt.org/sbt-native-packager/index.html
enablePlugins(GraalVMNativeImagePlugin)
graalVMNativeImageOptions := Seq(
  "--no-fallback",
  "--language:js",
  "--language:python",
  "--initialize-at-build-time=scala.runtime.Statics$VM"
)

scalacOptions ++= Seq("-feature", "-deprecation", "-Xlint:unused")
