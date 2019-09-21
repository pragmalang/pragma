import Dependencies._

ThisBuild / scalaVersion := "2.13.0"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "com.heavenlyx"
ThisBuild / organizationName := "heavenlyx"

lazy val root = (project in file("."))
  .settings(
    name := "heavenly-x",
    libraryDependencies += scalaTest % Test
  )

libraryDependencies += "org.parboiled" %% "parboiled" % "2.1.8"

scalacOptions ++= Seq("-feature", "-deprecation")

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
